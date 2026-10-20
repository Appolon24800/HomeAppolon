package dev.appolon.homelab.arr.feature.radarr.queue.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.QueueResource
import dev.appolon.homelab.arr.api.radarr.model.QueueStatusResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.queue.data.QueueRemoveOptions
import dev.appolon.homelab.arr.feature.radarr.queue.data.QueueWriteOutcome
import dev.appolon.homelab.arr.feature.radarr.queue.data.QueuedQueueWrites
import dev.appolon.homelab.arr.feature.radarr.queue.data.RadarrQueueRepository
import dev.appolon.homelab.arr.feature.radarr.queue.logic.QueuePresenters
import dev.appolon.homelab.arr.feature.radarr.queue.logic.RadarrQueueFilter
import dev.appolon.homelab.arr.feature.radarr.queue.logic.RadarrQueueItemUi
import dev.appolon.homelab.arr.feature.radarr.queue.logic.RadarrQueueOverview
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transient results of write actions, mapped to strings by the screen. */
enum class QueueNotice {
    REMOVED,
    REMOVE_QUEUED,
    RETRY_SENT,
    RETRY_QUEUED,
    GRABBED,
    GRAB_QUEUED,
}

/** Content payload of the queue screen's LCE state. */
data class RadarrQueueContent(
    val items: List<RadarrQueueItemUi>,
    val overview: RadarrQueueOverview?,
    val pendingActions: Int,
)

/**
 * Queue screen state. List data flows from the repository's store-backed
 * flows; live updates arrive through [refreshSignals] (the composition root
 * maps `ArrEvent.QueueChanged` from the LiveConnectionManager onto it, which
 * also makes tests a plain MutableSharedFlow). Writes surface as notices and
 * set the queued indicator on the touched rows.
 */
class RadarrQueueViewModel(
    private val serverId: ServerId,
    private val repository: RadarrQueueRepository,
    refreshSignals: Flow<ServerId>,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val filter: RadarrQueueFilter = RadarrQueueFilter.ALL,
        val allItems: List<RadarrQueueItemUi> = emptyList(),
        val overview: RadarrQueueOverview? = null,
        val pendingActions: Int = 0,
        val notice: QueueNotice? = null,
        val writeError: ArrError? = null,
    ) {
        /** The filter chip may hide every row; the empty pane only shows on an empty queue. */
        val filteredItems: List<RadarrQueueItemUi> get() = QueuePresenters.filter(allItems, filter)
        val queueEmpty: Boolean get() = allItems.isEmpty()
    }

    private data class Snapshots(
        val items: List<QueueResource> = emptyList(),
        val status: QueueStatusResource? = null,
        val writes: QueuedQueueWrites = QueuedQueueWrites(),
        val pending: Int = 0,
    )

    private var snapshots = Snapshots()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrQueueContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.allItems.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.queueEmpty -> LceState.Empty
                else -> LceState.Content(
                    RadarrQueueContent(
                        items = s.filteredItems,
                        overview = s.overview,
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    /** Aggregate completion for the shell's server switcher badge. */
    val overallProgress: StateFlow<Float?> = state
        .map { it.overview?.overallProgress }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Four independent single-flow collectors instead of one combine:
        // each store-backed flow re-emits reliably on its own, and the folded
        // snapshot keeps every permutation consistent regardless of the order
        // the emissions interleave.
        viewModelScope.launch {
            repository.observeItems().collect {
                snapshots = snapshots.copy(items = it)
                publishSnapshots()
            }
        }
        viewModelScope.launch {
            repository.observeStatus().collect {
                snapshots = snapshots.copy(status = it)
                publishSnapshots()
            }
        }
        viewModelScope.launch {
            repository.queuedWrites().collect {
                snapshots = snapshots.copy(writes = it)
                publishSnapshots()
            }
        }
        viewModelScope.launch {
            repository.queuedCount().collect {
                snapshots = snapshots.copy(pending = it)
                publishSnapshots()
            }
        }
        refresh()
        viewModelScope.launch {
            // One refresh per burst of live events; other servers' signals are ignored.
            refreshSignals
                .filter { it == serverId }
                .debounce(LIVE_REFRESH_DEBOUNCE_MILLIS)
                .collect { refresh() }
        }
    }

    private fun publishSnapshots() {
        val current = snapshots
        _state.update { s ->
            s.copy(
                loaded = true,
                allItems = QueuePresenters.present(
                    current.items,
                    current.writes.queuedItemIds(current.items),
                ),
                overview = QueuePresenters.overviewOf(current.items, current.status),
                pendingActions = current.pending,
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refresh()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(loaded = true, loadError = null, refreshing = false)
                }

                is ArrResult.Err -> _state.update { s ->
                    s.copy(
                        loaded = true,
                        loadError = if (s.allItems.isEmpty()) result.error else null,
                        writeError = if (s.allItems.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun setFilter(filter: RadarrQueueFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun removeItem(item: RadarrQueueItemUi, options: QueueRemoveOptions) {
        viewModelScope.launch {
            when (val result = repository.removeItem(item.id, item.movieId, options)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        notice = if (result.value is QueueWriteOutcome.Queued) {
                            QueueNotice.REMOVE_QUEUED
                        } else {
                            QueueNotice.REMOVED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(writeError = result.error, notice = null) }
            }
        }
    }

    fun retryImport(item: RadarrQueueItemUi) {
        val downloadId = item.downloadId ?: return
        viewModelScope.launch {
            when (val result = repository.retryImport(downloadId, item.outputPath)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        notice = if (result.value is QueueWriteOutcome.Queued) {
                            QueueNotice.RETRY_QUEUED
                        } else {
                            QueueNotice.RETRY_SENT
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(writeError = result.error, notice = null) }
            }
        }
    }

    fun grab(item: RadarrQueueItemUi) {
        viewModelScope.launch {
            when (val result = repository.grab(item.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        notice = if (result.value is QueueWriteOutcome.Queued) {
                            QueueNotice.GRAB_QUEUED
                        } else {
                            QueueNotice.GRABBED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(writeError = result.error, notice = null) }
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }

    private companion object {
        const val LIVE_REFRESH_DEBOUNCE_MILLIS = 250L
    }
}
