package dev.appolon.homelab.arr.feature.prowlarr.history.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.prowlarr.history.data.HistoryPresentation
import dev.appolon.homelab.arr.feature.prowlarr.history.data.HistoryRow
import dev.appolon.homelab.arr.feature.prowlarr.history.data.ProwlarrHistoryRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.WriteOutcome
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

/** Transient one-line result of the clear action, surfaced as a snackbar. */
sealed interface HistoryMessage {
    data object Cleared : HistoryMessage
    data object ClearQueued : HistoryMessage
    data class ClearFailed(val error: ArrError) : HistoryMessage
}

/**
 * State of the history screen. [rows] is the filtered presentation of every
 * cached event; [totalCount] is the server's count for the active indexer
 * filter, and [endReached] turns off infinite scroll once the window is
 * fully loaded.
 */
data class HistoryUiState(
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val loadError: ArrError? = null,
    val rows: List<HistoryRow> = emptyList(),
    val totalCount: Int = 0,
    val endReached: Boolean = false,
    val loadingMore: Boolean = false,
    val appendError: ArrError? = null,
    val indexerFilter: Int? = null,
    val query: String = "",
    val indexers: List<IndexerResource> = emptyList(),
    val confirmClear: Boolean = false,
    val clearQueued: Boolean = false,
    val message: HistoryMessage? = null,
) {
    /** Name of the selected indexer filter chip, or null when showing all. */
    fun indexerFilterLabel(): String? = indexerFilter?.let { id ->
        indexers.firstOrNull { it.id == id }?.name ?: "#$id"
    }
}

/**
 * ViewModel of the Prowlarr history screen: paged query log with an indexer
 * filter (server-side), free-text filtering (client-side over the loaded
 * window), infinite scroll, and the clear-history command with offline
 * queueing.
 */
class HistoryViewModel(
    private val repository: ProwlarrHistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<HistoryUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.rows.isEmpty() && !s.clearQueued ->
                    LceState.Error(s.loadError)

                !s.loaded && s.rows.isEmpty() -> LceState.Loading
                s.rows.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeHistory()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { events -> rebuild(events) }
        }
        viewModelScope.launch {
            repository.observeIndexers()
                .catch { }
                .collect { indexers ->
                    _state.update { it.copy(indexers = indexers) }
                    rebuild(repository.cachedHistory())
                }
        }
        viewModelScope.launch {
            repository.clearQueued()
                .catch { }
                .collect { queued -> _state.update { it.copy(clearQueued = queued) } }
        }
        refresh()
    }

    /** Pull-to-refresh, error retry, and post-clear reload entry point. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            repository.indexers()
            when (val result = repository.loadPage(FIRST_PAGE, activeIndexerIds())) {
                is ArrResult.Ok -> _state.update { s ->
                    val pageRecords = result.value.records.orEmpty().size
                    s.copy(
                        loaded = true,
                        refreshing = false,
                        loadError = null,
                        appendError = null,
                        totalCount = result.value.totalRecords,
                        endReached = endReached(pageRecords, result.value.totalRecords),
                    )
                }
                is ArrResult.Err -> _state.update {
                    it.copy(loaded = true, refreshing = false, loadError = result.error)
                }
            }
        }
    }

    /** Loads the next page while the window still has events left. */
    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.endReached || current.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, appendError = null) }
            val page = repository.cachedHistory().size / ProwlarrHistoryRepository.PAGE_SIZE + 1
            when (val result = repository.loadPage(page, activeIndexerIds())) {
                is ArrResult.Ok -> {
                    val loadedCount = repository.cachedHistory().size
                    _state.update { s ->
                        s.copy(
                            loadingMore = false,
                            totalCount = result.value.totalRecords,
                            endReached = endReached(loadedCount, result.value.totalRecords),
                        )
                    }
                }
                is ArrResult.Err -> _state.update {
                    it.copy(loadingMore = false, appendError = result.error)
                }
            }
        }
    }

    /** Free-text filter over the loaded window; no reload needed. */
    fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        viewModelScope.launch { rebuild(repository.cachedHistory()) }
    }

    /** Switches the indexer filter and reloads page one server-side. */
    fun setIndexerFilter(indexerId: Int?) {
        if (indexerId == _state.value.indexerFilter) return
        _state.update { it.copy(indexerFilter = indexerId, loaded = false, totalCount = 0) }
        refresh()
    }

    fun requestClear() = _state.update { it.copy(confirmClear = true) }

    fun dismissClear() = _state.update { it.copy(confirmClear = false) }

    /** Runs the ClearHistory command, queued offline with optimistic clearing. */
    fun confirmClear() {
        _state.update { it.copy(confirmClear = false) }
        viewModelScope.launch {
            when (val outcome = repository.clearHistory()) {
                is ArrResult.Ok -> {
                    _state.update { s ->
                        s.copy(
                            message = if (outcome.value is WriteOutcome.Queued) {
                                HistoryMessage.ClearQueued
                            } else {
                                HistoryMessage.Cleared
                            },
                            endReached = true,
                        )
                    }
                    refresh()
                }
                is ArrResult.Err -> _state.update {
                    it.copy(message = HistoryMessage.ClearFailed(outcome.error))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun activeIndexerIds(): List<Int>? =
        _state.value.indexerFilter?.let(::listOf)

    /**
     * The window is fully loaded once the server reports no more records, or
     * a short page arrives (servers that cannot total exactly still stop).
     */
    private fun endReached(loadedCount: Int, totalRecords: Int): Boolean =
        loadedCount >= totalRecords ||
            loadedCount % ProwlarrHistoryRepository.PAGE_SIZE != 0

    private fun rebuild(events: List<HistoryResource>) {
        _state.update { s ->
            s.copy(
                rows = HistoryPresentation.present(events, s.indexers, s.query),
                loaded = true,
            )
        }
    }

    private companion object {
        private const val FIRST_PAGE = 1
    }
}
