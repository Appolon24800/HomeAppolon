package dev.appolon.homelab.arr.data.store

import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.QueuedAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionQueueImplTest {

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
