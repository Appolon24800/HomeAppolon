package dev.appolon.homelab.arr.feature.prowlarr.stats.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.prowlarr.stats.data.StatsPeriod
import dev.appolon.homelab.arr.feature.prowlarr.stats.data.StatsPresentation
import dev.appolon.homelab.arr.feature.prowlarr.stats.data.StatsTotals
import dev.appolon.homelab.arr.feature.prowlarr.stats.data.ProwlarrStatsRepository
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

/**
 * State of the stats screen. [stats] is the raw window for [period]; the
 * screen derives sections through [totals] and [StatsPresentation].
 */
data class StatsUiState(
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val loadError: ArrError? = null,
    val period: StatsPeriod = StatsPeriod.WEEK,
    val stats: IndexerStatsResource? = null,
) {
    val totals: StatsTotals
        get() = stats?.indexers.orEmpty().let(StatsPresentation::totals)

    val indexerRows: List<dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatistics>
        get() = stats?.indexers.orEmpty().let(StatsPresentation::sortedByQueries)
}

/**
 * ViewModel of the Prowlarr stats screen: one server, one selected period,
 * cache-first loads so revisiting a period inside the TTL is instant.
 */
class StatsViewModel(
    private val repository: ProwlarrStatsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<StatsUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.stats == null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.stats == null || !StatsPresentation.hasData(s.stats) -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeAll()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { rows ->
                    val current = _state.value
                    rows.firstOrNull { it.period == current.period.wireName }?.let { cached ->
                        _state.update {
                            it.copy(stats = cached.resource, loaded = true)
                        }
                    }
                }
        }
        load(_state.value.period)
    }

    /** Pull-to-refresh and error retry entry point; forces the network. */
    fun refresh() {
        val period = _state.value.period
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refresh(period)) {
                is ArrResult.Ok ->
                    _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }

    /** Switches the aggregation window; cached data for it shows immediately. */
    fun setPeriod(period: StatsPeriod) {
        if (period == _state.value.period) return
        viewModelScope.launch {
            val cached = repository.cachedStats(period)
            _state.update {
                it.copy(period = period, stats = cached, loaded = cached != null, loadError = null)
            }
            load(period)
        }
    }

    private fun load(period: StatsPeriod) {
        viewModelScope.launch {
            when (val result = repository.stats(period)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(loaded = true, loadError = null, stats = result.value)
                }
                is ArrResult.Err -> _state.update {
                    it.copy(loaded = true, loadError = result.error)
                }
            }
        }
    }
}
