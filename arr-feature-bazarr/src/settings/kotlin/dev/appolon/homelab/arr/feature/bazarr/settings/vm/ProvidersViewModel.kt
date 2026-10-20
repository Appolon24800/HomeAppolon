package dev.appolon.homelab.arr.feature.bazarr.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.settings.data.BazarrSettingsRepository
import dev.appolon.homelab.arr.feature.bazarr.settings.data.SettingsWriteOutcome
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrProviderStatus
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

/** Transient one-line result of a provider reset, surfaced as a snackbar. */
sealed interface ProvidersMessage {
    data object ResetSynced : ProvidersMessage
    data object ResetQueued : ProvidersMessage
    data class WriteFailed(val error: ArrError) : ProvidersMessage
}

/** State of the subtitle providers screen. */
data class ProvidersUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val providers: List<BazarrProviderStatus> = emptyList(),
    val queuedReset: Boolean = false,
    val resetting: Boolean = false,
    val refreshing: Boolean = false,
    val message: ProvidersMessage? = null,
)

/**
 * ViewModel of the subtitle providers screen. The bundled spec exposes
 * provider health plus a whole-fleet reset, so rows carry whatever dynamic
 * fields the server reports and the reset applies to every provider.
 */
class ProvidersViewModel(
    private val repository: BazarrSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProvidersUiState())
    val state: StateFlow<ProvidersUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<ProvidersUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.providers.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.providers.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeProviders()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { rows -> _state.update { it.copy(providers = rows, loaded = true) } }
        }
        viewModelScope.launch {
            repository.queuedProviderReset()
                .catch { }
                .collect { queued -> _state.update { it.copy(queuedReset = queued) } }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = repository.providers()) {
                is ArrResult.Ok -> _state.update { it.copy(loadError = null) }
                is ArrResult.Err -> _state.update { it.copy(loadError = result.error) }
            }
        }
    }

    /** Pull-to-refresh and error retry entry point; bypasses the cache TTL. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.providers(BazarrSettingsRepository.FORCE_REFRESH)) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }

    /** Resets every provider's status and retry counters. */
    fun resetProviders() {
        viewModelScope.launch {
            _state.update { it.copy(resetting = true) }
            val result = repository.resetProviders()
            _state.update {
                it.copy(
                    resetting = false,
                    message = when (result) {
                        is ArrResult.Ok -> when (result.value) {
                            is SettingsWriteOutcome.Synced -> ProvidersMessage.ResetSynced
                            is SettingsWriteOutcome.Queued -> ProvidersMessage.ResetQueued
                        }

                        is ArrResult.Err -> ProvidersMessage.WriteFailed(result.error)
                    },
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
