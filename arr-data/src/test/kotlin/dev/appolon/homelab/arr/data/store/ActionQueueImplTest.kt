package dev.appolon.homelab.arr.data.store

import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.QueuedAction
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionQueueImplTest {

    @Test
    fun interruptedLegacyTerminalMoveDoesNotReplayTheFailedAction() = runTest {
        val store = SqliteEntityStore(InMemoryArrDatabase())
        val queue = ActionQueueImpl(store)
        val failed = action("legacy", 1, attempts = 5)
        store.put(ActionQueueImpl.QUEUE_SCOPE, ActionQueueImpl.DEAD_TYPE, "legacy", failed, QueuedAction.serializer())
        queue.enqueue(failed.copy(attempts = 4))
        assertEquals(0 to 0, queue.replay { error("Terminal action must stay failed") })
        assertTrue(queue.pending().isEmpty())
        assertEquals(listOf(failed), queue.deadLetters())
    }

    @Test
    fun manualRetryAndAutomaticReplayDoNotExecuteTheSameActionTogether() = runTest {
        val queue = ActionQueueImpl(SqliteEntityStore(InMemoryArrDatabase(), ioDispatcher = Dispatchers.Unconfined))
        queue.enqueue(action("once", 1))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val replay = async {
            queue.replay {
                calls++
                started.complete(Unit)
                release.await()
                true
            }
        }
        started.await()
        assertEquals(false, queue.retryOne(ActionId("once")) { calls++; true })
        release.complete(Unit)
        assertEquals(1 to 0, replay.await())
        assertEquals(1, calls)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun automaticReplayDoesNotOverlapAManualRetry() = runTest {
        val queue = ActionQueueImpl(SqliteEntityStore(InMemoryArrDatabase(), ioDispatcher = Dispatchers.Unconfined))
        queue.enqueue(action("once", 1))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val retry = async {
            queue.retryOne(ActionId("once")) {
                started.complete(Unit)
                release.await()
                true
            }
        }
        started.await()
        assertEquals(0 to 0, queue.replay { error("Must not send twice") })
        release.complete(Unit)
        assertTrue(retry.await())
    }

    @Test
    fun cancelledFailedActionRetrySurvivesAQueueRestartWithAFreshBudget() = runTest {
        val db = InMemoryArrDatabase()
        val queue = ActionQueueImpl(SqliteEntityStore(db), maxAttempts = 1)
        queue.enqueue(action("restored", 1))
        queue.replay { false }
        try {
            queue.retryOne(ActionId("restored")) {
                assertEquals(0, it.attempts)
                assertEquals(null, it.lastError)
                throw CancellationException("Process stopped after restoring")
            }
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            val restarted = ActionQueueImpl(SqliteEntityStore(db))
            assertTrue(restarted.deadLetters().isEmpty())
            assertEquals(action("restored", 1), restarted.pending().single())
            assertEquals(1 to 0, restarted.replay { true })
        }
    }

    @Test
    fun discardWaitsForReplayAndDoesNotResurrectAFailedWrite() = runTest {
        val queue = ActionQueueImpl(SqliteEntityStore(InMemoryArrDatabase(), ioDispatcher = Dispatchers.Unconfined))
        queue.enqueue(action("discard", 1))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val replay = async {
            queue.replay {
                started.complete(Unit)
                release.await()
                false
            }
        }
        started.await()
        val discard = launch(start = CoroutineStart.UNDISPATCHED) { queue.remove(ActionId("discard")) }
        assertEquals(false, discard.isCompleted)
        release.complete(Unit)
        replay.await()
        discard.join()
        assertTrue(queue.pending().isEmpty())
        assertTrue(queue.deadLetters().isEmpty())
    }

    @Test
    fun manualRetryDoesNotTouchOtherActionsOrSpendItsAttemptBudget() = runTest {
        val queue = ActionQueueImpl(SqliteEntityStore(InMemoryArrDatabase()))
        queue.enqueue(action("retry", 1, attempts = 2))
        queue.enqueue(action("other", 2))
        assertEquals(false, queue.retryOne(ActionId("retry")) { throw IOException("Offline") })
        assertEquals(listOf(action("retry", 1, attempts = 2), action("other", 2)), queue.pending())
        assertTrue(queue.retryOne(ActionId("retry")) { true })
        assertEquals(listOf(action("other", 2)), queue.pending())
        assertEquals(false, queue.retryOne(ActionId("missing")) { error("Missing action") })
    }

    @Test
    fun aTransportExceptionDoesNotBlockLaterActions() = runTest {
        val queue = ActionQueueImpl(SqliteEntityStore(InMemoryArrDatabase()))
        queue.enqueue(action("unreachable", enqueuedAt = 1))
        queue.enqueue(action("reachable", enqueuedAt = 2))

        val result = queue.replay {
            if (it.actionId.value == "unreachable") throw IOException("Connection refused")
            true
        }

        assertEquals(1 to 0, result)
        assertEquals("unreachable", queue.pending().single().actionId.value)
        assertEquals(1, queue.pending().single().attempts)
    }

    @Test
    fun cancellationKeepsTheActionAndItsAttemptBudget() = runTest {
        val queue = ActionQueueImpl(SqliteEntityStore(InMemoryArrDatabase()))
        queue.enqueue(action("cancelled", enqueuedAt = 1))
        try {
            queue.replay { throw CancellationException("Stopped") }
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(0, queue.pending().single().attempts)
        }
        assertEquals(1 to 0, queue.replay { true })
    }

    @Test
    fun deletingAServerRemovesItsPendingAndFailedWritesOnly() = runTest {
        val queue = ActionQueueImpl(SqliteEntityStore(InMemoryArrDatabase()), maxAttempts = 1)
        queue.enqueue(action("failed", enqueuedAt = 1))
        queue.replay { false }
        queue.enqueue(action("pending", enqueuedAt = 2))
        val other = action("other", enqueuedAt = 3).copy(serverId = ServerId("server-2"))
        queue.enqueue(other)

        queue.removeForServer(ServerId("server-1"))

        assertEquals(listOf(other), queue.pending())
        assertTrue(queue.deadLetters().isEmpty())
    }

    private fun action(id: String, enqueuedAt: Long, attempts: Int = 0) = QueuedAction(
        actionId = ActionId(id),
        serverId = ServerId("server-1"),
        appType = AppType.RADARR,
        method = "POST",
        path = "/api/v3/command",
        bodyJson = """{"name":"MoviesSearch"}""",
        description = "Search for movie",
        enqueuedAtMillis = enqueuedAt,
        attempts = attempts,
    )

    @Test
    fun enqueueThreeSucceedTwoFailOne() = runTest {
        val queue = ActionQueueImpl(SqliteEntityStore(InMemoryArrDatabase()), maxAttempts = 3)
        queue.enqueue(action("late", enqueuedAt = 30))
        queue.enqueue(action("early", enqueuedAt = 10))
        queue.enqueue(action("middle", enqueuedAt = 20))

        val invocationOrder = mutableListOf<String>()
        val result = queue.replay { queued ->
            invocationOrder.add(queued.actionId.value)
            // The last action in replay order fails.
            queued.actionId.value != "late"
        }

        assertEquals(Pair(2, 0), result)
        assertEquals(listOf("early", "middle", "late"), invocationOrder)

        val pending = queue.pending()
        assertEquals(1, pending.size)
        assertEquals("late", pending.single().actionId.value)
        assertEquals(1, pending.single().attempts)
        assertEquals("replay attempt 1 failed", pending.single().lastError)
        assertTrue(queue.deadLetters().isEmpty())
    }

    @Test
    fun pendingListsOldestFirstAcrossQueueRestarts() = runTest {
        val db = InMemoryArrDatabase()
        val first = ActionQueueImpl(SqliteEntityStore(db))
        first.enqueue(action("late", enqueuedAt = 30))
        first.enqueue(action("early", enqueuedAt = 10))
        first.enqueue(action("middle", enqueuedAt = 20))

        // A rebuilt queue reads persistence, not memory.
        val second = ActionQueueImpl(SqliteEntityStore(db))
        assertEquals(
            listOf("early", "middle", "late"),
            second.pending().map { it.actionId.value },
        )
    }

    @Test
    fun exhaustedActionsMoveToDeadLettersInsteadOfRetryingForever() = runTest {
        val queue = ActionQueueImpl(SqliteEntityStore(InMemoryArrDatabase()), maxAttempts = 3)
        queue.enqueue(action("doomed", enqueuedAt = 1))

        repeat(2) {
            assertEquals(Pair(0, 0), queue.replay { false })
        }
        assertEquals(2, queue.pending().single().attempts)

        // Third failure reaches maxAttempts: terminal, counted, and preserved.
        assertEquals(Pair(0, 1), queue.replay { false })
        assertTrue(queue.pending().isEmpty())

        val dead = queue.deadLetters()
        assertEquals(1, dead.size)
        assertEquals(3, dead.single().attempts)
        assertEquals("doomed", dead.single().actionId.value)
    }

    @Test
    fun replayStopsRetryingAnActionAfterTerminalFailure() = runTest {
        val queue = ActionQueueImpl(SqliteEntityStore(InMemoryArrDatabase()), maxAttempts = 1)
        queue.enqueue(action("one-shot", enqueuedAt = 1))

        assertEquals(Pair(0, 1), queue.replay { false })
        // The dead letter is not replayed again, and it does not report success.
        assertEquals(Pair(0, 0), queue.replay { true })
    }

    @Test
    fun removeClearsPendingAndDeadEntries() = runTest {
        val queue = ActionQueueImpl(SqliteEntityStore(InMemoryArrDatabase()), maxAttempts = 1)
        queue.enqueue(action("kept", enqueuedAt = 1))
        queue.enqueue(action("dropped", enqueuedAt = 2))
        queue.replay { it.actionId.value != "dropped" }
        assertEquals(1, queue.deadLetters().size)

        queue.remove(ActionId("dropped"))
        assertTrue(queue.pending().isEmpty())
        assertTrue(queue.deadLetters().isEmpty())

        queue.remove(ActionId("kept"))
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun enqueuePersistsTheFullActionForReplay() = runTest {
        val db = InMemoryArrDatabase()
        val queue = ActionQueueImpl(SqliteEntityStore(db))
        val original = action("full", enqueuedAt = 5)

        queue.enqueue(original)

        val stored = queue.pending().single()
        assertEquals(original, stored)
        // Persisted under the synthetic scope and type, keyed by action id.
        val queueRowKey = Triple(ActionQueueImpl.QUEUE_SCOPE.value, "action.queue", "full")
        assertTrue(db.rows.containsKey(queueRowKey))
    }
}
