package dev.appolon.homelab.arr.servers.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.data.RetryableActionQueue
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pending and failed writes with queue-owned retry and discard operations. */
class SyncStatusViewModel(
    private val queue: RetryableActionQueue,
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

    private var observation: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        observation?.cancel()
        observation = viewModelScope.launch {
            try {
                queue.observe().collect { pending ->
                    val dead = queue.deadLetters()
                    _state.update {
                        it.copy(pending = pending, dead = dead, loaded = true, loadError = null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loaded = true, loadError = e.arrError()) }
            }
        }
    }

    /** Replays the whole queue oldest first through the queue's own replay. */
    fun retryAll() = work {
        val result = queue.replay(executor)
        _state.update { it.copy(lastResult = result) }
    }

    /** The queue serializes this attempt with background replay and discard. */
    fun retryOne(actionId: ActionId) = work {
        queue.retryOne(actionId, executor)
    }

    fun discard(actionId: ActionId) = work {
        queue.remove(actionId)
    }

    private fun work(operation: suspend () -> Unit) {
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                operation()
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadError = e.arrError()) }
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }
}
