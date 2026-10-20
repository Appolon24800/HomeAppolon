package dev.appolon.homelab.arr.feature.radarr.settings.system.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.radarr.model.HealthCheckResult
import dev.appolon.homelab.arr.api.radarr.model.HealthResource
import dev.appolon.homelab.arr.api.radarr.model.TaskResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.system.FakeSystemActionQueue
import dev.appolon.homelab.arr.feature.radarr.settings.system.FakeSystemEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.system.data.RadarrSettingsSystemRepository
import dev.appolon.homelab.arr.ui.lce.LceState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrSystemStatusViewModelTest {

    private lateinit var api: FakeRadarrSystem
    private lateinit var store: FakeSystemEntityStore
    private lateinit var queue: FakeSystemActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeRadarrSystem()
        store = FakeSystemEntityStore()
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

    private fun TestScope.withViewModel(): RadarrSystemStatusViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrSystemStatusViewModel(repository())
        backgroundScope.launch { vm.lce.collect { } }
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `loads status tasks disk and health into the content`() = runTest {
        api.systemStatusResult = dev.appolon.homelab.arr.api.radarr.model.SystemResource(
            appName = "Radarr",
            version = "5.20.0.8710",
            migrationVersion = 200,
            databaseType = dev.appolon.homelab.arr.api.radarr.model.DatabaseType.SQLITE,
            databaseVersion = "3.44",
        )
        api.taskItems += TaskResource(
            id = 1,
            name = "Backup",
            interval = 1440,
            lastExecution = "2026-10-19T00:00:00Z",
            lastDuration = "00:00:02.5000000",
        )
        api.diskSpaceItems += DiskSpaceResource(id = 1, path = "/media", freeSpace = 100, totalSpace = 400)
        api.healthItems += HealthResource(id = 1, type = HealthCheckResult.WARNING, message = "Check recycle bin")

        val vm = withViewModel()
        val content = (vm.lce.value as LceState.Content).data

        assertEquals("5.20.0.8710", content.status.version)
        assertEquals(200, content.status.migrationVersion)
        val task = content.tasks.single()
        assertEquals(1440, task.intervalMinutes)
        assertEquals(2L, task.lastDurationSeconds)
        assertEquals(100L, content.diskSpace.single().freeSpace)
        assertEquals(HealthCheckResult.WARNING, content.health.single().type)
    }

    @Test
    fun `health checks sort errors before notices`() = runTest {
        api.healthItems += HealthResource(id = 1, type = HealthCheckResult.NOTICE)
        api.healthItems += HealthResource(id = 2, type = HealthCheckResult.ERROR)
        api.healthItems += HealthResource(id = 3, type = HealthCheckResult.WARNING)

        val vm = withViewModel()
        val content = (vm.lce.value as LceState.Content).data

        assertEquals(
            listOf(HealthCheckResult.ERROR, HealthCheckResult.WARNING, HealthCheckResult.NOTICE),
            content.health.map { it.type },
        )
    }

    @Test
    fun `status stays content when a later refresh fails`() = runTest {
        api.systemStatusResult = dev.appolon.homelab.arr.api.radarr.model.SystemResource(appName = "Radarr")
        val vm = withViewModel()

        api.errorFor = { java.io.IOException("offline") }
        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Content)
    }
}
