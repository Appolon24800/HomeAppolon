package dev.appolon.homelab.arr.feature.bazarr.series.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrEpisode
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLibraryRepository
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMediaKind
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrSeries
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

/**
 * State of the episodes browser of one series: the header series row and the
 * episodes grouped by season, each carrying its own subtitle state.
 */
data class BazarrEpisodesUiState(
    val seriesId: Int,
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val series: BazarrSeries? = null,
    val episodes: List<BazarrEpisode> = emptyList(),
    val refreshing: Boolean = false,
) {

    /** Episodes grouped by ascending season, episodes ascending inside each season. */
    val seasons: List<Pair<Int, List<BazarrEpisode>>> by lazy(LazyThreadSafetyMode.NONE) {
        episodes.groupBy { it.season }
            .toSortedMap()
            .map { (season, list) -> season to list.sortedBy { it.episode } }
    }
}

/**
 * ViewModel of the episodes browser. Reads the owning series from the
 * library cache for the header (refreshing it when stale) and loads the
 * episode list cache-first; like every arr screen it survives process death
 * through the entity store.
 */
class BazarrEpisodesViewModel(
    private val repository: BazarrLibraryRepository,
    private val seriesId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(BazarrEpisodesUiState(seriesId = seriesId))
    val state: StateFlow<BazarrEpisodesUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<BazarrEpisodesUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.episodes.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.episodes.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeSeries()
                .catch { }
                .collect { series ->
                    _state.update { s ->
                        s.copy(series = series.firstOrNull { it.seriesId == seriesId })
                    }
                }
        }
        refresh()
    }

    /** Pull-to-refresh and error retry entry point. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            // The header reads the series row; refresh it when the cache is
            // stale so a deep link still shows title and progress.
            repository.refreshIfStale(BazarrMediaKind.SERIES)
            when (val result = repository.episodes(seriesId)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(episodes = result.value, loaded = true, refreshing = false, loadError = null)
                }
                is ArrResult.Err -> _state.update { s ->
                    s.copy(
                        loaded = true,
                        refreshing = false,
                        loadError = if (s.episodes.isEmpty()) result.error else s.loadError,
                    )
                }
            }
        }
    }
}
