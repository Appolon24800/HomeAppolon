package dev.appolon.homelab.arr.servers.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sync status state: the pending action queue plus the dead-letter list the
 * queue implementation keeps for actions that exhausted their attempts.
 * "Retry now" runs one action through the injected executor (and removes it
 * on success without touching its attempt count), while "Retry all" goes
 * through the queue's own replay so its attempt and dead-letter bookkeeping
 * stays in one place. "Retry now" also resolves dead letters: they requeue
 * with a fresh attempt budget before their immediate attempt. While any
 * retry runs, [UiState.working] disables the retry buttons so a replay and
 * a manual retry cannot double-execute the same action.
 */
class SyncStatusViewModel(
    private val queue: ActionQueue,
    private val deadLetterSource: suspend () -> List<QueuedAction> = { emptyList() },
    private val executor: suspend (QueuedAction) -> Boolean,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val pending: List<QueuedAction> = emptyList(),
        val dead: List<QueuedAction> = emptyList(),
        val working: Boolean = false,
        val lastResult: Pair<Int, Int>? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<UiState>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.pending.isEmpty() && s.dead.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.working)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            queue.observe()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { pending ->
                    _state.update { it.copy(pending = pending, loaded = true) }
                    reloadDead()
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { reloadDead() }
                .onFailure { t ->
                    _state.update { it.copy(loaded = true, loadError = t.arrError()) }
                }
        }
    }

    /** Replays the whole queue oldest first through the queue's own replay. */
    fun retryAll() {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            runCatching { queue.replay(executor) }
                .onSuccess { (succeeded, dead) ->
                    _state.update {
                        it.copy(working = false, lastResult = succeeded to dead)
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(working = false, loadError = t.arrError()) }
                }
            refresh()
        }
    }

    /** Runs one action immediately; on success it leaves the queue, on failure it stays. */
    fun retryOne(actionId: ActionId) {
        if (_state.value.working) return
        viewModelScope.launch {
            val current = _state.value
            val deadAction = current.dead.firstOrNull { it.actionId == actionId }
            val action = current.pending.firstOrNull { it.actionId == actionId }
                ?: deadAction
                ?: return@launch
            _state.update { it.copy(working = true) }
            if (deadAction != null) {
                // A dead letter requeues explicitly with a fresh attempt
                // budget so later replays pick it up again; remove() also
                // clears the dead-row copy, leaving exactly one entry.
                try {
                    queue.remove(actionId)
                    queue.enqueue(deadAction.copy(attempts = 0, lastError = null))
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update { it.copy(working = false, loadError = t.arrError()) }
                    return@launch
                }
            }
            val succeeded = try {
                executor(action)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                false
            }
            if (succeeded) {
                try {
                    queue.remove(actionId)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update { it.copy(working = false, loadError = t.arrError()) }
                    return@launch
                }
            }
            _state.update { it.copy(working = false) }
            refresh()
        }
    }

    fun discard(actionId: ActionId) {
        viewModelScope.launch {
            runCatching { queue.remove(actionId) }
                .onFailure { t -> _state.update { it.copy(loadError = t.arrError()) } }
            refresh()
        }
    }

    private suspend fun reloadDead() {
        _state.update { it.copy(dead = deadLetterSource(), loaded = true) }
    }
}
