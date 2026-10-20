package dev.appolon.homelab.arr.feature.bazarr.settings.vm

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSubtitles
import dev.appolon.homelab.arr.api.bazarr.BazarrSystemCall
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.settings.newSettingsRepository
import dev.appolon.homelab.arr.feature.bazarr.settings.settingsDataPayload
import dev.appolon.homelab.arr.feature.bazarr.settings.taskRow
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var system: FakeBazarrSystem
    private lateinit var vm: SchedulerViewModel

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

    private fun buildVm(): SchedulerViewModel = SchedulerViewModel(
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
        system.responses["get_system_tasks"] = settingsDataPayload(
            listOf(
                taskRow(jobId = "update_series", name = "Update Series"),
                taskRow(jobId = "update_movies", name = "Update Movies"),
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
    fun `initial load lists tasks with next runs`() = runVm {
        assertTrue(vm.lce.value is LceState.Content)
        assertEquals(2, vm.state.value.tasks.size)
        assertTrue(vm.state.value.tasks.first().nextRunMillis > 0)
    }

    @Test
    fun `online run now starts the task`() = runVm {
        val task = vm.state.value.tasks.first { it.jobId == "update_series" }

        vm.runNow(task)

        assertEquals(SchedulerMessage.RunSynced, vm.state.value.message)
        assertTrue(vm.state.value.tasks.first { it.jobId == "update_series" }.running)
        val call = system.calls.single { it.operation == "post_system_tasks" }
        assertEquals("update_series", call.arguments["taskid"])
    }

    @Test
    fun `offline run now queues and keeps the task marked running`() = runVm {
        system.failure = failing("post_system_tasks")
        val task = vm.state.value.tasks.first { it.jobId == "update_movies" }

        vm.runNow(task)

        assertEquals(SchedulerMessage.RunQueued, vm.state.value.message)
        assertEquals(setOf("update_movies"), vm.state.value.queuedTaskIds)
        assertTrue(vm.state.value.tasks.first { it.jobId == "update_movies" }.running)
    }
}
