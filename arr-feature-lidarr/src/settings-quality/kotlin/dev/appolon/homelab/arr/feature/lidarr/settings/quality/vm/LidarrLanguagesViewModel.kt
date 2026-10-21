package dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.LanguageResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.data.LidarrQualityRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Content payload of the languages LCE state: the read-only lookup table. */
data class LidarrLanguagesContent(
    val languages: List<LanguageResource> = emptyList(),
)

/**
 * Read-only language list state. Languages feed the language custom format
 * conditions; editing never happens here, so there are no writes to queue.
 */
class LidarrLanguagesViewModel(
    private val repository: LidarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val languages: List<LanguageResource> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrLanguagesContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(LidarrLanguagesContent(s.languages), refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeLanguages().collect { languages ->
                _state.update { it.copy(loaded = true, loadError = null, languages = languages) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshIfStale()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false) }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (!it.loaded) result.error else null,
                        refreshing = false,
                    )
                }
            }
        }
    }
}
