package dev.appolon.homelab.arr.feature.radarr.settings.system.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.LogFileResource
import dev.appolon.homelab.arr.api.radarr.model.UpdateResource
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrUpdatesViewModelTest {

    private lateinit var api: FakeRadarrSystem
    private lateinit var commands: FakeRadarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeSystemActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeRadarrSystem()
        commands = FakeRadarrQueue()
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
        commandApi = commands,
        actionQueue = queue,
        now = { now },
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.withViewModel(): RadarrUpdatesViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrUpdatesViewModel(repository())
        backgroundScope.launch { vm.lce.collect { } }
        advanceUntilIdle()
        return vm
    }

    private fun seed() {
        api.systemStatusResult = dev.appolon.homelab.arr.api.radarr.model.SystemResource(
            appName = "Radarr",
            version = "5.20.0.8710",
            branch = "master",
        )
        api.updateItems += UpdateResource(
            id = 1,
            version = "5.20.0.8710",
            installed = true,
            latest = true,
        )
        api.updateItems += UpdateResource(
            id = 2,
            version = "5.30.0.8710",
            installed = false,
            installable = true,
        )
    }

    @Test
    fun `loads updates with the current row derived`() = runTest {
        seed()
        val vm = withViewModel()
        val content = (vm.lce.value as LceState.Content).data

        assertEquals("5.20.0.8710", content.status!!.version)
        val current = content.updates.first { it.isCurrent }
        assertEquals("5.20.0.8710", current.update.version)
        val installable = content.updates.filter { it.installable }
        assertEquals(listOf("5.30.0.8710"), installable.map { it.update.version })
    }

    @Test
    fun `install dispatches the ApplicationUpdate command`() = runTest {
        seed()
        val vm = withViewModel()
        vm.install()
        advanceUntilIdle()

        assertEquals("ApplicationUpdate", commands.commandItems.single().name)
        assertTrue(vm.state.value.notice == UpdatesNotice.INSTALL_STARTED)
    }

    @Test
    fun `offline install queues the command`() = runTest {
        seed()
        val vm = withViewModel()

        commands.errorFor = { IOException("offline") }
        vm.install()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == UpdatesNotice.INSTALL_QUEUED)
        assertEquals("api/v3/command", queue.pending().single().path)
    }

    @Test
    fun `checkForUpdates bypasses the TTL`() = runTest {
        seed()
        val vm = withViewModel()
        api.updateItems.clear()
        api.updateItems += UpdateResource(id = 1, version = "5.20.0.8710", installed = true, latest = true)

        vm.checkForUpdates()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == UpdatesNotice.CHECKED)
    }

    @Test
    fun `openLogFile loads contents and close clears them`() = runTest {
        seed()
        api.updateLogFileItems += LogFileResource(id = 1, filename = "update.1.txt")
        api.updateLogFileContentsText = "log line"
        val vm = withViewModel()

        vm.openLogFile(vm.state.value.logFiles.first())
        advanceUntilIdle()
        assertEquals("log line", vm.state.value.logContents)

        vm.closeLogFile()
        assertNull(vm.state.value.openLogFile)
        assertNull(vm.state.value.logContents)
    }

    @Test
    fun `log load failure surfaces the error without crashing`() = runTest {
        seed()
        api.updateLogFileItems += LogFileResource(id = 1, filename = "update.1.txt")
        val vm = withViewModel()

        api.errorFor = { IOException("offline") }
        vm.openLogFile(vm.state.value.logFiles.first())
        advanceUntilIdle()

        assertTrue(vm.state.value.logError is dev.appolon.homelab.arr.core.ArrError.Network)
        assertNull(vm.state.value.logContents)
    }
}
