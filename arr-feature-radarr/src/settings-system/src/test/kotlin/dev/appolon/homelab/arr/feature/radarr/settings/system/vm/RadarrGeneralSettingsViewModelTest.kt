package dev.appolon.homelab.arr.feature.radarr.settings.system.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.AuthenticationType
import dev.appolon.homelab.arr.api.radarr.model.HostConfigResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.system.FakeSystemActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.system.data.RadarrSettingsSystemRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrGeneralSettingsViewModelTest {

    private lateinit var api: FakeRadarrSystem
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeSystemActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeRadarrSystem()
        store = FakeEntityStore()
        queue = FakeSystemActionQueue()
        store.clock = { now }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.repository() = RadarrSettingsSystemRepository(
        serverId = ServerId("s1"),
        store = store,
        hostConfigApi = api,
        backupApi = api,
        updateApi = api,
        updateLogFileApi = api,
        systemApi = api,
        taskApi = api,
        diskSpaceApi = api,
        healthApi = api,
        tagApi = api,
        tagDetailsApi = api,
        autoTaggingApi = api,
        commandApi = FakeRadarrQueue(),
        actionQueue = queue,
        now = { now },
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.withViewModel(): RadarrGeneralSettingsViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrGeneralSettingsViewModel(repository())
        backgroundScope.launch { vm.lce.collect { } }
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `loads the host config into the draft with a blank password`() = runTest {
        api.hostConfigState = HostConfigResource(
            id = 1,
            port = 7878,
            urlBase = "/radarr",
            authenticationMethod = AuthenticationType.FORMS,
            username = "admin",
            password = "server-secret",
        )

        val vm = withViewModel()
        val content = (vm.lce.value as LceState.Content).data

        assertEquals("/radarr", content.draft.urlBase)
        assertEquals("admin", content.draft.username)
        assertEquals("", content.draft.password)
        assertFalse(content.dirty)
    }

    @Test
    fun `save applies the draft and keeps a blank password unchanged`() = runTest {
        api.hostConfigState = HostConfigResource(id = 1, port = 7878)

        val vm = withViewModel()
        vm.edit { it.copy(port = "9110", analyticsEnabled = true) }
        vm.save()
        advanceUntilIdle()

        assertEquals(9110, api.hostConfigState.port)
        assertTrue(api.hostConfigState.analyticsEnabled)
        assertNull(api.hostConfigState.password)
        assertTrue(vm.state.value.notice == GeneralSettingsNotice.SAVED)
    }

    @Test
    fun `save with a typed password forwards it but never caches it`() = runTest {
        api.hostConfigState = HostConfigResource(id = 1, port = 7878, password = "old")

        val vm = withViewModel()
        vm.edit { it.copy(authenticationMethod = AuthenticationType.BASIC, password = "new-secret") }
        vm.save()
        advanceUntilIdle()

        assertEquals("new-secret", api.hostConfigState.password)
        assertNull(vm.state.value.writeError)
        val cached = store.getAll(
            ServerId("s1"),
            RadarrSettingsSystemRepository.HOST_CONFIG_TYPE,
            HostConfigResource.serializer(),
        ).single()
        assertNull(cached.password)
    }

    @Test
    fun `switching auth to none clears credentials on save`() = runTest {
        api.hostConfigState = HostConfigResource(
            id = 1,
            port = 7878,
            authenticationMethod = AuthenticationType.FORMS,
            username = "admin",
            password = "pw",
        )

        val vm = withViewModel()
        vm.edit { it.copy(authenticationMethod = AuthenticationType.NONE) }
        vm.save()
        advanceUntilIdle()

        assertNull(api.hostConfigState.username)
        assertNull(api.hostConfigState.password)
    }

    @Test
    fun `an invalid port blocks save and flags the error`() = runTest {
        api.hostConfigState = HostConfigResource(id = 1, port = 7878)

        val vm = withViewModel()
        vm.edit { it.copy(port = "not-a-port") }
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content).data
        assertTrue(content.portError)
        vm.save()
        advanceUntilIdle()
        assertEquals(7878, api.hostConfigState.port)
    }

    @Test
    fun `offline save queues and clears the dirty flag optimistically`() = runTest {
        api.hostConfigState = HostConfigResource(id = 1, port = 7878)

        val vm = withViewModel()
        api.errorFor = { IOException("offline") }
        vm.edit { it.copy(port = "7000") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == GeneralSettingsNotice.SAVED_QUEUED)
        assertEquals("PUT", queue.pending().single().method)
        assertTrue(queue.pending().single().path.startsWith("api/v3/config/host/"))
        assertFalse(vm.state.value.dirty)
    }
}
