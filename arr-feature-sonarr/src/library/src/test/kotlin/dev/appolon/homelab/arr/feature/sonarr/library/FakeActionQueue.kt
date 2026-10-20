package dev.appolon.homelab.arr.feature.sonarr.library

import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.QueuedAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory action queue recording every enqueued action for assertions. */
class FakeActionQueue : ActionQueue {

    private val actions = MutableStateFlow<List<QueuedAction>>(emptyList())
    val enqueued: List<QueuedAction> get() = actions.value

    override suspend fun enqueue(action: QueuedAction) {
        actions.value = actions.value + action
    }

    override suspend fun pending(): List<QueuedAction> = actions.value

    override fun observe(): Flow<List<QueuedAction>> = actions

    override suspend fun replay(executor: suspend (QueuedAction) -> Boolean): Pair<Int, Int> {
        var ok = 0
        var failed = 0
        for (action in actions.value) {
            if (executor(action)) ok++ else failed++
        }
        return ok to failed
    }

    override suspend fun remove(actionId: ActionId) {
        actions.value = actions.value.filterNot { it.actionId == actionId }
    }
}
