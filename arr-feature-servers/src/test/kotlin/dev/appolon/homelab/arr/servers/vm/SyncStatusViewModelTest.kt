package dev.appolon.homelab.arr.servers.vm

import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModelTest {

    private val main = UnconfinedTestDispatcher()

    /**
     * Minimal queue mirroring the real replay contract: executor true means
     * remove, false means keep. The dead-letter list is a separate mutable
     * source like the real DEAD_TYPE scope.
     */
    private class FakeActionQueue : ActionQueue {
        val pendingFlow = MutableStateFlow<List<QueuedAction>>(emptyList())
        var dead: List<QueuedAction> = emptyList()

        override suspend fun enqueue(action: QueuedAction) {
            pendingFlow.value += action
        }

        override suspend fun pending(): List<QueuedAction> = pendingFlow.value

        override fun observe() = pendingFlow

        override suspend fun replay(executor: suspend (QueuedAction) -> Boolean): Pair<Int, Int> {
            var succeeded = 0
            var failed = 0
            pendingFlow.value.toList().forEach { action ->
                if (executor(action)) {
                    pendingFlow.value = pendingFlow.value - action
                    succeeded++
                } else {
                    failed++
                }
            }
            return succeeded to failed
        }

        override suspend fun remove(actionId: ActionId) {
            pendingFlow.value = pendingFlow.value.filterNot { it.actionId == actionId }
        }
    }

    private fun action(id: String, path: String) = QueuedAction(
        actionId = ActionId(id),
        serverId = ServerId("s1"),
        appType = AppType.RADARR,
        method = "POST",
        path = path,
        description = "Rename movie",
        enqueuedAtMillis = 1_000L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(
        queue: FakeActionQueue,
        executor: suspend (QueuedAction) -> Boolean,
    ): SyncStatusViewModel = SyncStatusViewModel(
        queue = queue,
        deadLetterSource = { queue.dead },
        executor = executor,
    )

    @Test
    fun `no pending and no dead letters renders empty`() = runTest {
        val queue = FakeActionQueue()
        val vm = vm(queue) { true }
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        assertEquals(LceState.Empty, vm.lce.value)
        job.cancel()
    }

    @Test
    fun `pending actions render as content`() = runTest {
        val queue = FakeActionQueue().apply { enqueue(action("a1", "/api/v3/movie")) }
        val vm = vm(queue) { true }
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(1, state.pending.size)
        assertTrue(state.loaded)
        job.cancel()
    }

    @Test
    fun `retry all keeps failures queued and reports the split`() = runTest {
        val queue = FakeActionQueue().apply {
            enqueue(action("a1", "/ok"))
            enqueue(action("a2", "/conflict"))
        }
        val vm = vm(queue) { action -> action.path == "/ok" }
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()

        vm.retryAll()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1 to 1, state.lastResult)
        assertEquals(listOf("a2"), state.pending.map { it.actionId.value })
        job.cancel()
    }

    @Test
    fun `retry one removes only the retried action on success`() = runTest {
        val queue = FakeActionQueue().apply {
            enqueue(action("a1", "/ok"))
            enqueue(action("a2", "/ok"))
        }
        val vm = vm(queue) { true }
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()

        vm.retryOne(ActionId("a1"))
        advanceUntilIdle()

        assertEquals(listOf("a2"), queue.pending().map { it.actionId.value })
        assertEquals(null, vm.state.value.lastResult)
        job.cancel()
    }

    @Test
    fun `retry one that fails keeps the action`() = runTest {
        val queue = FakeActionQueue().apply { enqueue(action("a1", "/conflict")) }
        val vm = vm(queue) { false }
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()

        vm.retryOne(ActionId("a1"))
        advanceUntilIdle()

        assertEquals(listOf("a1"), queue.pending().map { it.actionId.value })
        job.cancel()
    }

    @Test
    fun `discard removes a pending action`() = runTest {
        val queue = FakeActionQueue().apply { enqueue(action("a1", "/ok")) }
        val vm = vm(queue) { true }
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()

        vm.discard(ActionId("a1"))
        advanceUntilIdle()

        assertTrue(queue.pending().isEmpty())
        job.cancel()
    }

    @Test
    fun `dead letters surface in state`() = runTest {
        val deadAction = action("d1", "/api/v3/movie/10").copy(attempts = 5)
        val queue = FakeActionQueue().apply { dead = listOf(deadAction) }
        val vm = vm(queue) { false }
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("d1"), vm.state.value.dead.map { it.actionId.value })
        job.cancel()
    }
}
