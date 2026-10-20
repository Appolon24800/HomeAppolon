package dev.appolon.homelab.arr.feature.sonarr.wanted.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.SonarrWantedRepository
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.WantedCounts
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.WantedFilters
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.WantedPresentation
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.WantedTab
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.presentWanted
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transient one-line result of a search dispatch, surfaced as a snackbar. */
sealed interface WantedMessage {
    data class EpisodeSearchStarted(val count: Int) : WantedMessage
    data class SeriesSearchStarted(val seriesId: Int) : WantedMessage
    data class SearchQueued(val count: Int) : WantedMessage
    data object SearchFailed : WantedMessage
}

/** One tab's list state; both tabs stay warm so switching is instant. */
data class WantedTabUiState(
    val loaded: Boolean = false,
    val error: ArrError? = null,
    val episodes: List<EpisodeResource> = emptyList(),
    val totalRecords: Int = 0,
    val endReached: Boolean = false,
    val loadingMore: Boolean = false,
    /** Set when the last page append failed; blocks auto-retry until refresh. */
    val loadMoreFailed: Boolean = false,
)

/**
 * State of the Sonarr wanted screen. Both tabs render from this one object so
 * the shell badge callback and the tab switcher read a single source of truth.
 */
data class WantedUiState(
    val tab: WantedTab = WantedTab.MISSING,
    val tabs: Map<WantedTab, WantedTabUiState> =
        WantedTab.entries.associateWith { WantedTabUiState() },
    val filterState: FilterState = FilterState(),
    val refreshing: Boolean = false,
    val bulkSearching: Boolean = false,
    val searchingEpisodeIds: Set<Int> = emptySet(),
    val searchingSeriesIds: Set<Int> = emptySet(),
    val queuedEpisodeIds: Set<Int> = emptySet(),
    val queuedSeriesIds: Set<Int> = emptySet(),
    val queuedForServer: Int = 0,
    val message: WantedMessage? = null,
) {

    /** State of the visible tab. */
    val active: WantedTabUiState get() = tabs.getValue(tab)

    /** Filter options from the active list. */
    val options: WantedFilters.Options by lazy(LazyThreadSafetyMode.NONE) {
        WantedFilters.optionsFrom(active.episodes)
    }

    /** The filtered, sorted presentation the list renders. */
    val presentation: WantedPresentation by lazy(LazyThreadSafetyMode.NONE) {
        presentWanted(active.episodes, options, filterState)
    }

    /** True when more server pages may exist behind the filtered window. */
    val canLoadMore: Boolean get() = !active.endReached && !active.loadingMore
}

/**
 * ViewModel of the Sonarr wanted screen. Owns no Android types so unit tests
 * construct it directly against fakes; the destination composable wires it to
 * the [dev.appolon.homelab.arr.data.ArrDataGraph] singletons. Tab refreshes
 * run sequentially because both tabs write one shared totals row.
 */
class WantedViewModel(
    private val repository: SonarrWantedRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WantedUiState())
    val state: StateFlow<WantedUiState> = _state.asStateFlow()

    /** Server totals for both lists; the shell badges its entry from this. */
    val counts: StateFlow<WantedCounts> = repository.observeCounts()
        .catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WantedCounts())

    val lce: StateFlow<LceState<WantedUiState>> = state
        .map { s ->
            val tab = s.active
            when {
                tab.error != null && tab.episodes.isEmpty() -> LceState.Error(tab.error)
                !tab.loaded -> LceState.Loading
                tab.episodes.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        // Observe both tabs so switching is instant and the counts stay live.
        WantedTab.entries.forEach { tab ->
            viewModelScope.launch {
                repository.observe(tab)
                    .catch { t -> updateTab(tab) { it.copy(loaded = true, error = t.arrError()) } }
                    .collect { episodes ->
                        updateTab(tab) { current ->
                            current.copy(
                                episodes = episodes,
                                loaded = true,
                                endReached = current.totalRecords <= episodes.size,
                            )
                        }
                    }
            }
        }
        viewModelScope.launch {
            repository.queuedSearchEpisodeIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedEpisodeIds = ids) } }
        }
        viewModelScope.launch {
            repository.queuedSearchSeriesIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedSeriesIds = ids) } }
        }
        viewModelScope.launch {
            repository.queuedCount()
                .catch { }
                .collect { count -> _state.update { it.copy(queuedForServer = count) } }
        }
        refreshAll()
    }

    /** Pull-to-refresh and error retry entry point. */
    fun refresh() = refreshAll()

    private fun refreshAll() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            // Sequential: both tabs write the shared totals row.
            WantedTab.entries.forEach { tab ->
                when (val result = repository.refresh(tab)) {
                    is ArrResult.Ok -> updateTab(tab) { current ->
                        current.copy(
                            totalRecords = result.value.totalRecords,
                            endReached = result.value.endReached(current.episodes.size),
                            error = null,
                            loadMoreFailed = false,
                        )
                    }

                    is ArrResult.Err -> updateTab(tab) { current ->
                        current.copy(
                            totalRecords = 0,
                            endReached = true,
                            error = result.error,
                        )
                    }
                }
            }
            _state.update { it.copy(refreshing = false) }
            maybeAutoFill()
        }
    }

    fun selectTab(tab: WantedTab) = _state.update { it.copy(tab = tab) }

    /**
     * Appends the next server page of the active tab; a no-op at the end of
     * the list, while a load runs, or after a failure until the next refresh.
     */
    fun loadMore() {
        val s = _state.value
        if (!s.canLoadMore || s.active.loadMoreFailed || s.refreshing) return
        val tab = s.tab
        updateTab(tab) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val result = repository.loadNextPage(tab)) {
                is ArrResult.Ok -> {
                    updateTab(tab) { current ->
                        current.copy(
                            totalRecords = result.value.meta.totalRecords,
                            endReached = result.value.endReached ||
                                result.value.meta.endReached(current.episodes.size),
                            loadingMore = false,
                            loadMoreFailed = false,
                        )
                    }
                    // A heavily filtered or short first page would otherwise
                    // leave the list too short to ever scroll; top it up.
                    maybeAutoFill()
                }

                is ArrResult.Err -> updateTab(tab) { current ->
                    current.copy(loadingMore = false, loadMoreFailed = true)
                }
            }
        }
    }

    /** Keeps loading pages until the visible list clears [AUTO_FILL_MIN_ITEMS]. */
    private fun maybeAutoFill() {
        val s = _state.value
        if (s.canLoadMore && !s.active.loadMoreFailed &&
            s.active.episodes.size < AUTO_FILL_MIN_ITEMS
        ) {
            loadMore()
        }
    }

    fun setFilterValue(id: String, value: FilterValue) = _state.update { s ->
        val next = if (value == emptyValue(id)) {
            s.filterState.without(id)
        } else {
            s.filterState.with(id, value)
        }
        s.copy(filterState = next)
    }

    /** Cycles the monitored toggle unset, on, off, unset. */
    fun toggleMonitored() {
        val current = (_state.value.filterState.value(WantedFilters.MONITORED)
            as? FilterValue.Toggle)?.on
        val next = when (current) {
            null -> true
            true -> false
            false -> null
        }
        setFilterValue(WantedFilters.MONITORED, FilterValue.Toggle(next))
    }

    /** Adds or removes one season from the choice filter. */
    fun toggleSeason(season: String) {
        val current = (_state.value.filterState.value(WantedFilters.SEASON)
            as? FilterValue.Choice)?.selected ?: emptySet()
        val next = if (season in current) current - season else current + season
        setFilterValue(WantedFilters.SEASON, FilterValue.Choice(next))
    }

    fun clearFilters() = _state.update { it.copy(filterState = FilterState()) }

    /** Automatic search for one listed episode. */
    fun searchEpisode(episodeId: Int) {
        val episode = _state.value.active.episodes.firstOrNull { it.id == episodeId } ?: return
        _state.update { it.copy(searchingEpisodeIds = it.searchingEpisodeIds + episodeId) }
        viewModelScope.launch {
            val outcome = repository.searchEpisodes(listOf(episodeId))
            _state.update { s ->
                s.copy(
                    searchingEpisodeIds = s.searchingEpisodeIds - episodeId,
                    message = messageFor(outcome, WantedMessage.EpisodeSearchStarted(1)),
                )
            }
        }
    }

    /** Automatic search for every episode of one series shown in the list. */
    fun searchSeries(seriesId: Int) {
        val hasEpisodes = _state.value.active.episodes.any { it.seriesId == seriesId }
        if (!hasEpisodes) return
        _state.update { it.copy(searchingSeriesIds = it.searchingSeriesIds + seriesId) }
        viewModelScope.launch {
            val outcome = repository.searchSeries(seriesId)
            _state.update { s ->
                s.copy(
                    searchingSeriesIds = s.searchingSeriesIds - seriesId,
                    message = messageFor(outcome, WantedMessage.SeriesSearchStarted(seriesId)),
                )
            }
        }
    }

    /** Bulk search over everything the filters currently present. */
    fun searchAll() {
        val episodeIds = _state.value.presentation.all.map { it.id }
        if (episodeIds.isEmpty()) return
        _state.update { it.copy(bulkSearching = true) }
        viewModelScope.launch {
            val outcome = repository.searchEpisodes(episodeIds)
            _state.update { s ->
                s.copy(
                    bulkSearching = false,
                    message = messageFor(
                        outcome,
                        WantedMessage.EpisodeSearchStarted(episodeIds.size),
                    ),
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun messageFor(
        outcome: ArrResult<WriteOutcome>,
        synced: WantedMessage,
    ): WantedMessage = when (outcome) {
        is ArrResult.Ok -> if (outcome.value is WriteOutcome.Queued) {
            val count = when (synced) {
                is WantedMessage.EpisodeSearchStarted -> synced.count
                is WantedMessage.SeriesSearchStarted -> 1
                else -> 1
            }
            WantedMessage.SearchQueued(count)
        } else {
            synced
        }

        is ArrResult.Err -> WantedMessage.SearchFailed
    }

    private fun updateTab(tab: WantedTab, transform: (WantedTabUiState) -> WantedTabUiState) =
        _state.update { s ->
            s.copy(tabs = s.tabs + (tab to transform(s.tabs.getValue(tab))))
        }

    private fun emptyValue(id: String): FilterValue = when (id) {
        WantedFilters.MONITORED -> FilterValue.Toggle(null)
        else -> FilterValue.Choice(emptySet())
    }

    companion object {
        /** Short first pages top up automatically so the list can scroll. */
        const val AUTO_FILL_MIN_ITEMS = 24
    }
}
