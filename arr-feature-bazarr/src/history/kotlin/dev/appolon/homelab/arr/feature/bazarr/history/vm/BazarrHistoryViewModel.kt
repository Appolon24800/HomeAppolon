package dev.appolon.homelab.arr.feature.bazarr.history.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrHistoryEvent
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageOption
import dev.appolon.homelab.arr.feature.bazarr.history.data.BazarrHistoryRepository
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

/** Which media kind the history feed shows. */
enum class HistoryMediaFilter { ALL, EPISODES, MOVIES }

/**
 * State of the subtitle history screen: the paged merged decision feed with
 * provider, score, and action per row, filterable by media kind and language.
 */
data class BazarrHistoryUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val events: List<BazarrHistoryEvent> = emptyList(),
    val languages: List<BazarrLanguageOption> = emptyList(),
    val selectedLanguage: String? = null,
    val mediaFilter: HistoryMediaFilter = HistoryMediaFilter.ALL,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val refreshing: Boolean = false,
) {
    /** Events after the media kind and language filters, newest first. */
    val presented: List<BazarrHistoryEvent>
        get() = events.filter { event ->
            val mediaMatch = when (mediaFilter) {
                HistoryMediaFilter.ALL -> true
                HistoryMediaFilter.EPISODES -> !event.isMovie
                HistoryMediaFilter.MOVIES -> event.isMovie
            }
            val languageMatch = selectedLanguage?.let { event.languageCode2 == it } ?: true
            mediaMatch && languageMatch
        }
}

/**
 * ViewModel of the subtitle decision history. The feed pages through the
 * repository: the screen calls [loadMore] as the list approaches its end,
 * and [refresh] resets paging. Read-only: history never queues writes.
 */
class BazarrHistoryViewModel(
    private val repository: BazarrHistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BazarrHistoryUiState())
    val state: StateFlow<BazarrHistoryUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<BazarrHistoryUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.events.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.events.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeHistory()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { events ->
                    _state.update {
                        it.copy(events = events, loaded = true, endReached = repository.historyExhausted())
                    }
                }
        }
        viewModelScope.launch {
            when (val result = repository.languages()) {
                is ArrResult.Ok -> _state.update { it.copy(languages = result.value) }
                is ArrResult.Err -> Unit
            }
        }
        refresh()
    }

    /** Pull-to-refresh and error retry entry point; resets paging. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshHistory()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }

    /** Appends the next page; a no-op once both feeds are exhausted. */
    fun loadMore() {
        if (_state.value.loadingMore || _state.value.endReached) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (val result = repository.loadMoreHistory()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        loadingMore = false,
                        loadError = null,
                        endReached = repository.historyExhausted() || result.value == 0,
                    )
                }

                is ArrResult.Err ->
                    _state.update { it.copy(loadingMore = false, loadError = result.error) }
            }
        }
    }

    fun setLanguageFilter(code2: String?) =
        _state.update { it.copy(selectedLanguage = if (it.selectedLanguage == code2) null else code2) }

    fun setMediaFilter(filter: HistoryMediaFilter) =
        _state.update { it.copy(mediaFilter = filter) }
}
