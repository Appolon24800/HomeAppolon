package dev.appolon.homelab.arr.feature.bazarr.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.settings.data.BazarrSettingsRepository
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrLanguageSetting
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

/** State of the languages settings screen. */
data class LanguagesUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val languages: List<BazarrLanguageSetting> = emptyList(),
    val refreshing: Boolean = false,
) {
    /** Enabled languages first; the repository delivers them pre-sorted. */
    val enabledCount: Int get() = languages.count { it.enabled }
}

/**
 * ViewModel of the languages settings screen. The enabled-language list is
 * read-only through the bundled Bazarr spec, so this screen presents the
 * server's flags; pull-to-refresh bypasses the cache TTL.
 */
class LanguagesViewModel(
    private val repository: BazarrSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LanguagesUiState())
    val state: StateFlow<LanguagesUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LanguagesUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.languages.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.languages.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeLanguages()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { rows -> _state.update { it.copy(languages = rows, loaded = true) } }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = repository.languages()) {
                is ArrResult.Ok -> _state.update { it.copy(loadError = null) }
                is ArrResult.Err -> _state.update { it.copy(loadError = result.error) }
            }
        }
    }

    /** Pull-to-refresh and error retry entry point; bypasses the cache TTL. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.languages(BazarrSettingsRepository.FORCE_REFRESH)) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }
}
