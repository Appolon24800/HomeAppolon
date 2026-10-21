package dev.appolon.homelab.arr.feature.bazarr.episodes.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.BazarrSubtitlesRepository
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrHistoryEvent
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageOption
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
 * State of the subtitle history screen: the merged episode and movie
 * decision feed with language chips from the Bazarr languages endpoint and
 * action chips derived from the feed itself.
 */
data class SubtitleHistoryUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val events: List<BazarrHistoryEvent> = emptyList(),
    val languages: List<BazarrLanguageOption> = emptyList(),
    val selectedLanguage: String? = null,
    val selectedAction: String? = null,
    val refreshing: Boolean = false,
) {
    /** Distinct action labels in the feed, for the action chip row. */
    val actions: List<String> get() = events.map { it.action }.filter { it.isNotBlank() }.distinct()

    /** Events after the language and action chip filters, newest first. */
    val presented: List<BazarrHistoryEvent>
        get() = events.filter { event ->
            val languageMatch = selectedLanguage?.let { event.languageCode2 == it } ?: true
            val actionMatch = selectedAction?.let { event.action == it } ?: true
            languageMatch && actionMatch
        }
}

/**
 * ViewModel of the subtitle decision history. Read-only: the feed refreshes
 * from both history endpoints and never queues writes.
 */
class SubtitleHistoryViewModel(
    private val repository: BazarrSubtitlesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SubtitleHistoryUiState())
    val state: StateFlow<SubtitleHistoryUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SubtitleHistoryUiState>> = state
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
                .collect { events -> _state.update { it.copy(events = events, loaded = true) } }
        }
        viewModelScope.launch {
            when (val result = repository.languages()) {
                is ArrResult.Ok -> _state.update { it.copy(languages = result.value) }
                is ArrResult.Err -> Unit
            }
        }
        refresh()
    }

    /** Pull-to-refresh and error retry entry point. */
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

    fun setLanguageFilter(code2: String?) =
        _state.update { it.copy(selectedLanguage = if (it.selectedLanguage == code2) null else code2) }

    fun setActionFilter(action: String?) =
        _state.update { it.copy(selectedAction = if (it.selectedAction == action) null else action) }
}
