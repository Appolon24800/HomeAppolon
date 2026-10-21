package dev.appolon.homelab.arr.feature.bazarr.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.settings.data.BazarrSettingsRepository
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrStatusRow
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

/** State of the general settings screen. */
data class GeneralUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val rows: List<BazarrStatusRow> = emptyList(),
    val refreshing: Boolean = false,
)

/**
 * ViewModel of the general settings screen. Bazarr's bundled spec has no
 * dedicated settings endpoints, so this page projects the status payload:
 * folder mappings, provider limits, and anti-captcha options appear whenever
 * the server reports them, with every remaining field under a second group.
 */
class GeneralViewModel(
    private val repository: BazarrSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GeneralUiState())
    val state: StateFlow<GeneralUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<GeneralUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.rows.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.rows.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeStatus()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { rows -> _state.update { it.copy(rows = rows, loaded = true) } }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = repository.status()) {
                is ArrResult.Ok -> _state.update { it.copy(loadError = null) }
                is ArrResult.Err -> _state.update { it.copy(loadError = result.error) }
            }
        }
    }

    /** Pull-to-refresh and error retry entry point; bypasses the cache TTL. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.status(BazarrSettingsRepository.FORCE_REFRESH)) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }
}
