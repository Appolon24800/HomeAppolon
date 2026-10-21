package dev.appolon.homelab.arr.data.store

import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex

/**
 * [ActionQueue] persisted through the [EntityStore] under a synthetic server
 * scope, so queued writes survive per-server cache wipes and replay never
 * needs the server list. Registry-generated ids are UUIDs (hex and dashes),
 * which cannot collide with the scope constant.
 *
 * A failing replay increments the attempt counter and stays pending. After
 * [maxAttempts] failures the action moves to the "action.queue.dead" scope
 * rather than being retried forever or silently dropped; the sync status
 * screen reads [deadLetters] to surface conflicts.
 */
class ActionQueueImpl(
    private val store: EntityStore,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) : ActionQueue {

    /** Serializes replays queue-wide; see [replay]. */
    private val replayGate = Mutex()

    override suspend fun enqueue(action: QueuedAction) {
        store.put(QUEUE_SCOPE, QUEUE_TYPE, action.actionId.value, action, QueuedAction.serializer())
    }

    override suspend fun pending(): List<QueuedAction> =
        store.getAll(QUEUE_SCOPE, QUEUE_TYPE, QueuedAction.serializer()).oldestFirst()

    override fun observe(): Flow<List<QueuedAction>> =
        store.observe(QUEUE_SCOPE, QUEUE_TYPE, QueuedAction.serializer())
            .map { it.oldestFirst() }

    override suspend fun replay(executor: suspend (QueuedAction) -> Boolean): Pair<Int, Int> {
        // Single-flight: an automatic trigger and a manual retry running at
        // the same time would execute the same action twice. A replay that
        // is already draining the queue covers this caller's work, so the
        // second entry reports no movement instead of racing.
        if (!replayGate.tryLock()) return 0 to 0
        try {
            var succeeded = 0
            var failedTerminally = 0
            pending().forEach { action ->
                if (executor(action)) {
                    remove(action.actionId)
                    succeeded++
                } else {
                    val bumped = action.copy(
                        attempts = action.attempts + 1,
                        lastError = "replay attempt ${action.attempts + 1} failed",
                    )
                    if (bumped.attempts >= maxAttempts) {
                        // Insert before delete so a crash between the two steps
                        // can never lose the action.
                        store.put(
                            QUEUE_SCOPE, DEAD_TYPE, bumped.actionId.value, bumped,
                            QueuedAction.serializer(),
                        )
                        store.delete(QUEUE_SCOPE, QUEUE_TYPE, bumped.actionId.value)
                        failedTerminally++
                    } else {
                        store.put(
                            QUEUE_SCOPE, QUEUE_TYPE, bumped.actionId.value, bumped,
                            QueuedAction.serializer(),
                        )
                    }
                }
            }
            return succeeded to failedTerminally
        } finally {
            replayGate.unlock()
        }
    }

    override suspend fun remove(actionId: ActionId) {
        store.delete(QUEUE_SCOPE, QUEUE_TYPE, actionId.value)
        store.delete(QUEUE_SCOPE, DEAD_TYPE, actionId.value)
    }

    /** Actions that exhausted [maxAttempts], oldest first. */
    suspend fun deadLetters(): List<QueuedAction> =
        store.getAll(QUEUE_SCOPE, DEAD_TYPE, QueuedAction.serializer()).oldestFirst()

    private fun List<QueuedAction>.oldestFirst(): List<QueuedAction> =
        sortedWith(compareBy({ it.enqueuedAtMillis }, { it.actionId.value }))

    companion object {
        internal val QUEUE_SCOPE = ServerId(SCOPE_VALUE)
        val QUEUE_TYPE = EntityType("action.queue")
        val DEAD_TYPE = EntityType("action.queue.dead")
        const val DEFAULT_MAX_ATTEMPTS = 5

        private const val SCOPE_VALUE = "__arr_action_queue__"
    }
}
