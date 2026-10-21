package dev.appolon.homelab.arr.feature.sonarr.activity.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.activity.data.ActivityListMeta
import dev.appolon.homelab.arr.feature.sonarr.activity.data.SonarrHistoryRepository
import dev.appolon.homelab.arr.feature.sonarr.activity.logic.ActivityPresenters
import dev.appolon.homelab.arr.feature.sonarr.activity.logic.HistoryDateWindow
import dev.appolon.homelab.arr.feature.sonarr.activity.logic.HistoryEventTypeFilter
import dev.appolon.homelab.arr.feature.sonarr.activity.logic.SonarrHistoryEventUi
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.ui.lce.LceState
import java.time.Instant
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
enum class HistoryNotice {
    MARKED_FAILED,
    MARK_FAIL_QUEUED,
}

/** Content payload of the history feed's LCE state. */
data class SonarrHistoryContent(
    val events: List<SonarrHistoryEventUi>,
    val canLoadMore: Boolean,
    val loadingMore: Boolean,
    val pendingActions: Int,
)

/**
 * History feed state. Events flow from the repository's store-backed flows;
 * the filters are pure state so chip switches are instant and offline-safe.
 * Live updates arrive through [refreshSignals] (the composition root maps
 * the LiveConnectionManager's queue events onto it). Infinite scroll calls
 * [loadMore], which stops early once a date window's oldest loaded row
 * predates the window start.
 */
class SonarrHistoryViewModel(
    private val serverId: ServerId,
    private val repository: SonarrHistoryRepository,
    refreshSignals: Flow<ServerId>,
    /** Injectable clock so date-window tests are deterministic. */
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val typeFilter: HistoryEventTypeFilter = HistoryEventTypeFilter.ALL,
        val dateWindow: HistoryDateWindow = HistoryDateWindow.ALL_TIME,
        val seriesQuery: String = "",
        val allEvents: List<SonarrHistoryEventUi> = emptyList(),
        val endReached: Boolean = false,
        val loadingMore: Boolean = false,
        /** Set when the last page append failed; blocks auto-retry until refresh. */
        val loadMoreFailed: Boolean = false,
        val pendingActions: Int = 0,
        val notice: HistoryNotice? = null,
        val writeError: ArrError? = null,
    ) {
        /** Filters may hide every row; the empty pane only shows on an empty feed. */
        val feedEmpty: Boolean get() = allEvents.isEmpty()
    }

    private data class Snapshots(
        val events: List<HistoryResource> = emptyList(),
        val meta: ActivityListMeta = ActivityListMeta(),
        val queuedIds: Set<Int> = emptySet(),
        val pending: Int = 0,
    )

    private var snapshots = Snapshots()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SonarrHistoryContent>> = state
        .map { s ->
            val at = now()
            val canLoadMore = ActivityPresenters.needsOlderPages(
                events = s.allEvents,
                window = s.dateWindow,
                endReached = s.endReached,
                now = at,
            ) && !s.loadingMore
            when {
                s.loadError != null && s.allEvents.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.feedEmpty -> LceState.Empty
                else -> LceState.Content(
                    SonarrHistoryContent(
                        events = ActivityPresenters.filter(
                            events = s.allEvents,
                            typeFilter = s.typeFilter,
                            window = s.dateWindow,
                            query = s.seriesQuery,
                            now = at,
                        ),
                        canLoadMore = canLoadMore,
                        loadingMore = s.loadingMore,
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        // Independent single-flow collectors, mirroring the queue ViewModel:
        // each store-backed flow re-emits reliably on its own and the folded
        // snapshot stays consistent under any interleaving.
        viewModelScope.launch {
            repository.observeEvents().collect {
                snapshots = snapshots.copy(events = it)
                publishSnapshots()
            }
        }
        viewModelScope.launch {
            repository.queuedMarkFailedIds().collect {
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
                allEvents = ActivityPresenters.present(current.events, current.queuedIds, now()),
                endReached = current.meta.endReached(current.events.size),
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
                    // Republish: the events collector may have published with the
                    // old meta before this assignment, leaving a stale endReached.
                    publishSnapshots()
                }

                is ArrResult.Err -> _state.update { s ->
                    s.copy(
                        loaded = true,
                        loadError = if (s.allEvents.isEmpty()) result.error else null,
                        writeError = if (s.allEvents.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    /** Appends the next server page when the window still allows more rows. */
    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.endReached || s.loadMoreFailed) return
        if (!ActivityPresenters.needsOlderPages(s.allEvents, s.dateWindow, s.endReached, now())) return
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

    fun setTypeFilter(filter: HistoryEventTypeFilter) {
        _state.update { it.copy(typeFilter = filter) }
    }

    fun setDateWindow(window: HistoryDateWindow) {
        _state.update { it.copy(dateWindow = window) }
    }

    fun setSeriesQuery(query: String) {
        _state.update { it.copy(seriesQuery = query) }
    }

    fun markFailed(event: SonarrHistoryEventUi) {
        viewModelScope.launch {
            when (val result = repository.markFailed(event.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        notice = if (result.value is WriteOutcome.Queued) {
                            HistoryNotice.MARK_FAIL_QUEUED
                        } else {
                            HistoryNotice.MARKED_FAILED
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
