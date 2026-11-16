package dev.appolon.homelab.arr.data.store

import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.data.RetryableActionQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
) : RetryableActionQueue {

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
                // Older builds could stop after copying a terminal failure
                // but before deleting its pending row. Keep the failed copy.
                if (store.get(QUEUE_SCOPE, DEAD_TYPE, action.actionId.value, QueuedAction.serializer()) != null) {
                    store.delete(QUEUE_SCOPE, QUEUE_TYPE, action.actionId.value)
                    return@forEach
                }
                val succeededNow = try {
                    executor(action)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                if (succeededNow) {
                    removeNow(action.actionId)
                    succeeded++
                } else {
                    val bumped = action.copy(
                        attempts = action.attempts + 1,
                        lastError = "replay attempt ${action.attempts + 1} failed",
                    )
                    if (bumped.attempts >= maxAttempts) {
                        store.move(
                            QUEUE_SCOPE, QUEUE_TYPE, DEAD_TYPE, bumped.actionId.value, bumped,
                            QueuedAction.serializer(),
                        )
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
        replayGate.withLock { removeNow(actionId) }
    }

    private suspend fun removeNow(actionId: ActionId) {
        store.delete(QUEUE_SCOPE, QUEUE_TYPE, actionId.value)
        store.delete(QUEUE_SCOPE, DEAD_TYPE, actionId.value)
    }

    /** Actions that exhausted [maxAttempts], oldest first. */
    override suspend fun deadLetters(): List<QueuedAction> =
        store.getAll(QUEUE_SCOPE, DEAD_TYPE, QueuedAction.serializer()).oldestFirst()

    override suspend fun retryOne(
        actionId: ActionId,
        executor: suspend (QueuedAction) -> Boolean,
    ): Boolean {
        if (!replayGate.tryLock()) return false
        try {
            val dead = store.get(QUEUE_SCOPE, DEAD_TYPE, actionId.value, QueuedAction.serializer())
            val action = if (dead != null) {
                dead.copy(attempts = 0, lastError = null).also { restored ->
                    store.move(
                        QUEUE_SCOPE, DEAD_TYPE, QUEUE_TYPE, actionId.value, restored,
                        QueuedAction.serializer(),
                    )
                }
            } else {
                store.get(QUEUE_SCOPE, QUEUE_TYPE, actionId.value, QueuedAction.serializer())
                    ?: return false
            }
            val succeeded = try {
                executor(action)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (succeeded) removeNow(actionId)
            return succeeded
        } finally {
            replayGate.unlock()
        }
    }

    /** Removes pending and failed writes when their server is deleted. */
    suspend fun removeForServer(serverId: ServerId) {
        replayGate.withLock {
            (pending() + deadLetters())
                .filter { it.serverId == serverId }
                .map { it.actionId }
                .distinct()
                .forEach { removeNow(it) }
        }
    }

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
