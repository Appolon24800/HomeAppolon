package dev.appolon.homelab.arr.feature.bazarr.settings.vm

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSubtitles
import dev.appolon.homelab.arr.api.bazarr.BazarrSystemCall
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.settings.backupRow
import dev.appolon.homelab.arr.feature.bazarr.settings.jobRow
import dev.appolon.homelab.arr.feature.bazarr.settings.logRow
import dev.appolon.homelab.arr.feature.bazarr.settings.newSettingsRepository
import dev.appolon.homelab.arr.feature.bazarr.settings.settingsDataPayload
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var system: FakeBazarrSystem
    private lateinit var vm: SystemViewModel

    /** Injects a transport failure for one operation id; every other call passes. */
    private fun failing(operation: String): (BazarrSystemCall) -> Throwable? =
        { call -> if (call.operation == operation) IOException() else null }

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(): SystemViewModel = SystemViewModel(
        repository = newSettingsRepository(
            system = system,
            subtitles = FakeBazarrSubtitles(),
            store = FakeEntityStore(),
            queue = FakeActionQueue(),
            io = main,
        ),
    )

    private fun runVm(
        block: suspend TestScope.() -> Unit,
    ) = runTest(main) {
        system = FakeBazarrSystem()
        system.responses["get_system_status"] = buildJsonObject {
            put("data", buildJsonObject { put("version", "1.6.2") })
        }
        system.responses["get_system_jobs"] = settingsDataPayload(
            listOf(jobRow(id = 2, queueName = "search_missing_subtitle")),
        )
        system.responses["get_system_backups"] = settingsDataPayload(
            listOf(backupRow(filename = "bazarr_backup.zip")),
        )
        system.responses["get_system_logs"] = settingsDataPayload(
            listOf(
                logRow(message = "Imported subtitles", type = "INFO"),
                logRow(message = "Provider failed", type = "ERROR", timestamp = 1_760_000_100),
            ),
        )
        vm = buildVm()
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        block()
        advanceUntilIdle()
        job.cancel()
    }

    @Test
    fun `initial load fills every feed`() = runVm {
        assertTrue(vm.lce.value is LceState.Content)
        val state = vm.state.value
        assertEquals(1, state.statusRows.size)
        assertEquals(1, state.jobs.size)
        assertEquals(1, state.backups.size)
        assertEquals(2, state.logs.size)
        // Newest log first.
        assertEquals("Provider failed", state.logs.first().message)
    }

    @Test
    fun `online job delete removes the row`() = runVm {
        vm.deleteJob(vm.state.value.jobs.single())

        assertEquals(SystemMessage.JobDeletedSynced, vm.state.value.message)
        assertTrue(vm.state.value.jobs.isEmpty())
        val call = system.calls.single { it.operation == "delete_system_jobs" }
        assertEquals(2, call.arguments["id"])
    }

    @Test
    fun `offline job delete queues and drops the row optimistically`() = runVm {
        system.failure = failing("delete_system_jobs")

        vm.deleteJob(vm.state.value.jobs.single())

        assertEquals(SystemMessage.JobDeletedQueued, vm.state.value.message)
        assertTrue(vm.state.value.jobs.isEmpty())
        assertEquals(setOf(2), vm.state.value.queuedJobIds)
    }

    @Test
    fun `empty queue on the jobs tab clears that queue only`() = runVm {
        system.responses["get_system_jobs"] = settingsDataPayload(
            listOf(
                jobRow(id = 2, queueName = "search_missing_subtitle"),
                jobRow(id = 3, queueName = "upgrade_subtitle"),
            ),
        )
        vm.refresh()
        assertEquals(2, vm.state.value.jobs.size)

        vm.emptyQueue("search_missing_subtitle")

        assertEquals(SystemMessage.QueueEmptiedSynced, vm.state.value.message)
        assertEquals(listOf(3), vm.state.value.jobs.map { it.id })
    }

    @Test
    fun `offline log rotation clears the cache and shows the queued signal`() = runVm {
        system.failure = failing("delete_system_logs")

        vm.rotateLogs()

        assertEquals(SystemMessage.LogsRotatedQueued, vm.state.value.message)
        assertTrue(vm.state.value.logs.isEmpty())
        assertTrue(vm.state.value.queuedLogRotation)
    }

    @Test
    fun `online backup delete removes the row`() = runVm {
        vm.deleteBackup(vm.state.value.backups.single())

        assertEquals(SystemMessage.BackupDeletedSynced, vm.state.value.message)
        assertTrue(vm.state.value.backups.isEmpty())
    }

    @Test
    fun `log type filter narrows the presented rows`() = runVm {
        vm.setLogTypeFilter("ERROR")

        assertEquals(listOf("Provider failed"), vm.state.value.presentedLogs.map { it.message })
        assertEquals(listOf("ERROR", "INFO"), vm.state.value.logTypes)

        vm.setLogTypeFilter(null)

        assertEquals(2, vm.state.value.presentedLogs.size)
    }

    @Test
    fun `every feed failing surfaces the error state`() = runTest(main) {
        system = FakeBazarrSystem()
        system.failure = { IOException() }
        vm = buildVm()
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)

        job.cancel()
    }
}
