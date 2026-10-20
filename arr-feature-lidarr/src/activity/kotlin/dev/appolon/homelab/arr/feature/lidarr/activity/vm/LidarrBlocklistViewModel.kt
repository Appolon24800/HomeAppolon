package dev.appolon.homelab.arr.feature.lidarr.activity.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.BlocklistResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.activity.data.LidarrActivityListMeta
import dev.appolon.homelab.arr.feature.lidarr.activity.data.LidarrBlocklistRepository
import dev.appolon.homelab.arr.feature.lidarr.activity.logic.LidarrActivityPresenters
import dev.appolon.homelab.arr.feature.lidarr.activity.logic.LidarrBlocklistEntryUi
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
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
enum class LidarrBlocklistNotice {
    REMOVED,
    REMOVE_QUEUED,
    CLEARED,
    CLEAR_QUEUED,
}

/** Content payload of the blocklist's LCE state. */
data class LidarrBlocklistContent(
    val entries: List<LidarrBlocklistEntryUi>,
    val canLoadMore: Boolean,
    val loadingMore: Boolean,
    val pendingActions: Int,
)

/**
 * Blocklist screen state. Entries flow from the repository's store-backed
 * flows; removals apply optimistically offline and badge their rows through
 * the queued-writes projection until replay finishes. Live updates arrive
 * through [refreshSignals] like the history feed.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class LidarrBlocklistViewModel(
    private val serverId: ServerId,
    private val repository: LidarrBlocklistRepository,
    refreshSignals: Flow<ServerId>,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val entries: List<LidarrBlocklistEntryUi> = emptyList(),
        val endReached: Boolean = false,
        val loadingMore: Boolean = false,
        /** Set when the last page append failed; blocks auto-retry until refresh. */
        val loadMoreFailed: Boolean = false,
        val pendingActions: Int = 0,
        val notice: LidarrBlocklistNotice? = null,
        val writeError: ArrError? = null,
    )

    private data class Snapshots(
        val entries: List<BlocklistResource> = emptyList(),
        val meta: LidarrActivityListMeta = LidarrActivityListMeta(),
        val queuedIds: Set<Int> = emptySet(),
        val pending: Int = 0,
    )

    private var snapshots = Snapshots()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrBlocklistContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.entries.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.entries.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    LidarrBlocklistContent(
                        entries = s.entries,
                        canLoadMore = !s.endReached && !s.loadingMore,
                        loadingMore = s.loadingMore,
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeEntries().collect {
                snapshots = snapshots.copy(entries = it)
                publishSnapshots()
            }
        }
        viewModelScope.launch {
            repository.queuedRemovedIds().collect {
                snapshots = snapshots.copy(queuedIds = it)
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
                entries = LidarrActivityPresenters.presentBlocklist(
                    current.entries,
                    current.queuedIds,
                ),
                endReached = current.meta.endReached(current.entries.size),
                pendingActions = current.pending,
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refresh()) {
                is ArrResult.Ok -> {
                    snapshots = snapshots.copy(meta = result.value)
                    _state.update {
                        it.copy(
                            loaded = true,
                            loadError = null,
                            refreshing = false,
                            loadMoreFailed = false,
                        )
                    }
                    // Republish: the entries collector may have published with the
                    // old meta before this assignment, leaving a stale endReached.
                    publishSnapshots()
                }

                is ArrResult.Err -> _state.update { s ->
                    s.copy(
                        loaded = true,
                        loadError = if (s.entries.isEmpty()) result.error else null,
                        writeError = if (s.entries.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    /** Appends the next server page for infinite scroll. */
    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.endReached || s.loadMoreFailed) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }
            when (val result = repository.loadNextPage()) {
                is ArrResult.Ok -> {
                    snapshots = snapshots.copy(meta = result.value.meta)
                    _state.update { it.copy(loadingMore = false, loadMoreFailed = false) }
                    publishSnapshots()
                }

                is ArrResult.Err -> _state.update {
                    it.copy(loadingMore = false, loadMoreFailed = true)
                }
            }
        }
    }

    fun removeEntry(entry: LidarrBlocklistEntryUi) {
        viewModelScope.launch {
            when (val result = repository.removeEntry(entry.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        notice = if (result.value is WriteOutcome.Queued) {
                            LidarrBlocklistNotice.REMOVE_QUEUED
                        } else {
                            LidarrBlocklistNotice.REMOVED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(writeError = result.error, notice = null) }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            when (val result = repository.clearAll()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        notice = if (result.value is WriteOutcome.Queued) {
                            LidarrBlocklistNotice.CLEAR_QUEUED
                        } else {
                            LidarrBlocklistNotice.CLEARED
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
