package dev.appolon.homelab.arr.feature.prowlarr.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.api.prowlarr.model.ReleaseResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Independent LCE sections preserve cached content when a refresh fails. */
data class ProwlarrSearchState(
    val request: SearchRequest = SearchRequest(),
    val options: LceState<SearchOptions> = LceState.Loading,
    val results: LceState<List<ReleaseResource>> = LceState.Empty,
    val history: LceState<HistoryResourcePagingResource> = LceState.Loading,
    val stats: LceState<IndexerStatsResource> = LceState.Loading,
    val sort: SearchSort = SearchSort.AGE,
    val page: Int = 1,
    val days: Int = 7,
    val queued: Set<String> = emptySet(),
    val grabbing: Set<String> = emptySet(),
    val grabbed: Set<String> = emptySet(),
    val optionsError: ArrError? = null,
    val resultsError: ArrError? = null,
    val historyError: ArrError? = null,
    val statsError: ArrError? = null,
    val error: ArrError? = null,
)

/** Owns search controls, cancellation, cached loading, and optimistic grab status. */
class ProwlarrSearchViewModel(private val repository: ProwlarrSearchRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ProwlarrSearchState())
    val state: StateFlow<ProwlarrSearchState> = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var historyJob: Job? = null
    private var statsJob: Job? = null

    init {
        viewModelScope.launch {
            repository.queuedReleaseKeys().collect { keys ->
                mutableState.update { it.copy(queued = keys) }
            }
        }
        viewModelScope.launch {
            safely {
                repository.lastRequest()?.let { request ->
                    mutableState.update { it.copy(request = request) }
                    search()
                }
            }
            loadOptions()
            loadHistory()
            loadStats()
        }
    }

    fun setQuery(query: String) = mutableState.update {
        it.copy(request = it.request.copy(query = query))
    }

    fun toggleIndexer(id: Int) = mutableState.update {
        val selected = it.request.indexerIds
        it.copy(request = it.request.copy(
            indexerIds = if (id in selected) selected - id else selected + id,
        ))
    }

    fun toggleCategory(id: Int) = mutableState.update {
        val selected = it.request.categories
        it.copy(request = it.request.copy(
            categories = if (id in selected) selected - id else selected + id,
        ))
    }

    fun setSort(sort: SearchSort) = mutableState.update { it.copy(sort = sort) }

    fun loadOptions(force: Boolean = false) {
        viewModelScope.launch {
            safely {
                repository.cachedOptions()?.let { cached ->
                    mutableState.update { it.copy(options = LceState.Content(cached, true)) }
                }
                val result = repository.options(force)
                mutableState.update {
                    it.copy(
                        options = retained(it.options, result), optionsError = result.errorOrNull(),
                    )
                }
            }
        }
    }

    fun search(force: Boolean = false) {
        searchJob?.cancel()
        val request = state.value.request
        mutableState.update {
            it.copy(results = LceState.Loading, error = null, resultsError = null)
        }
        searchJob = viewModelScope.launch {
            safely {
                repository.rememberRequest(request)
                repository.cachedSearch(request)?.let { cached ->
                    mutableState.update { it.copy(results = LceState.Content(cached, true)) }
                }
                val result = repository.search(request, force)
                mutableState.update {
                    it.copy(results = retained(it.results, result) { rows -> rows.isEmpty() },
                        resultsError = result.errorOrNull())
                }
            }
        }
    }

    fun loadHistory(page: Int = 1, force: Boolean = false) {
        historyJob?.cancel()
        val indexers = state.value.request.indexerIds
        mutableState.update { it.copy(page = page.coerceAtLeast(1), history = LceState.Loading) }
        historyJob = viewModelScope.launch {
            safely {
                repository.cachedHistory(state.value.page, indexers)?.let { cached ->
                    mutableState.update { it.copy(history = LceState.Content(cached, true)) }
                }
                val result = repository.history(state.value.page, indexers, force)
                mutableState.update {
                    it.copy(history = retained(it.history, result) { page ->
                        page.records.isNullOrEmpty()
                    }, historyError = result.errorOrNull())
                }
            }
        }
    }

    fun loadStats(days: Int = state.value.days, force: Boolean = false) {
        require(days in listOf(1, 7, 30, 90))
        statsJob?.cancel()
        mutableState.update { it.copy(days = days, stats = LceState.Loading) }
        statsJob = viewModelScope.launch {
            safely {
                repository.cachedStats(days)?.let { cached ->
                    mutableState.update { it.copy(stats = LceState.Content(cached, true)) }
                }
                val result = repository.stats(days, force)
                mutableState.update {
                    it.copy(stats = retained(it.stats, result), statsError = result.errorOrNull())
                }
            }
        }
    }

    fun grab(release: ReleaseResource, clientId: Int) {
        val key = release.rowKey()
        if (key in state.value.grabbing || key in state.value.queued) return
        mutableState.update { it.copy(grabbing = it.grabbing + key, error = null) }
        viewModelScope.launch {
            val result = repository.grab(release, clientId)
            mutableState.update {
                it.copy(
                    grabbing = it.grabbing - key,
                    grabbed = if (result is ArrResult.Ok) it.grabbed + key else it.grabbed,
                    queued = if (result is ArrResult.Ok && result.value) {
                        it.queued + key
                    } else it.queued,
                    error = result.errorOrNull(),
                )
            }
        }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.arrError()
            mutableState.update {
                it.copy(
                    options = it.options.failedIfLoading(error),
                    results = it.results.failedIfLoading(error),
                    history = it.history.failedIfLoading(error),
                    stats = if (it.stats == LceState.Loading) LceState.Error(error) else it.stats,
                    error = error,
                )
            }
        }
    }
}

private fun <T> LceState<T>.failedIfLoading(error: ArrError): LceState<T> =
    if (this == LceState.Loading) LceState.Error(error) else this

private fun <T> ArrResult<T>.errorOrNull(): ArrError? = (this as? ArrResult.Err)?.error

private fun <T> retained(
    previous: LceState<T>,
    result: ArrResult<T>,
    empty: (T) -> Boolean = { false },
): LceState<T> = if (result is ArrResult.Err && previous is LceState.Content) {
    previous.copy(refreshing = false)
} else {
    LceState.fromResult(result, empty)
}
