package dev.appolon.homelab.arr.feature.bazarr.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.settings.data.BazarrSettingsRepository
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrLanguageProfile
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

/** State of the language profiles screen. */
data class ProfilesUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val profiles: List<BazarrLanguageProfile> = emptyList(),
    val refreshing: Boolean = false,
)

/**
 * ViewModel of the language profiles screen. Each profile renders its
 * cutoff, language slots with hearing impaired and forced flags, and its
 * must-contain and must-not-contain rules. The bundled spec exposes profiles
 * read-only, so the builder view presents what the server enforces.
 */
class ProfilesViewModel(
    private val repository: BazarrSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<ProfilesUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.profiles.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.profiles.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeProfiles()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { rows -> _state.update { it.copy(profiles = rows, loaded = true) } }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = repository.profiles()) {
                is ArrResult.Ok -> _state.update { it.copy(loadError = null) }
                is ArrResult.Err -> _state.update { it.copy(loadError = result.error) }
            }
        }
    }

    /** Pull-to-refresh and error retry entry point; bypasses the cache TTL. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.profiles(BazarrSettingsRepository.FORCE_REFRESH)) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }
}
