package dev.appolon.homelab.arr.feature.prowlarr.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.HealthResource
import dev.appolon.homelab.arr.api.prowlarr.model.SystemResource
import dev.appolon.homelab.arr.api.prowlarr.model.TaskResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.prowlarr.settings.data.ProwlarrSettingsRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Content payload of the system status LCE state. */
data class ProwlarrSystemStatusContent(
    val status: SystemResource,
    val tasks: List<TaskResource>,
    val health: List<HealthResource>,
)

/**
 * System status and scheduled task state: the server self-description
 * (version, runtime, database, authentication mode), the task table with
 * interval and last run, and the health findings with wiki remedies. All
 * read-only; the lifecycle controls stay in the web UI because restarting
 * a server from a phone invites lockouts.
 */
class ProwlarrSystemStatusViewModel(
    private val repository: ProwlarrSettingsRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val status: SystemResource? = null,
        val tasks: List<TaskResource> = emptyList(),
        val health: List<HealthResource> = emptyList(),
        val writeError: ArrError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<ProwlarrSystemStatusContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.status == null -> LceState.Error(s.loadError)
                !s.loaded || s.status == null -> LceState.Loading
                else -> LceState.Content(
                    ProwlarrSystemStatusContent(
                        status = s.status,
                        tasks = s.tasks,
                        health = s.health,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeSystemStatus().collect { status ->
                _state.update { it.copy(loaded = true, loadError = null, status = status) }
            }
        }
        viewModelScope.launch {
            repository.observeTasks().collect { tasks -> _state.update { it.copy(tasks = tasks) } }
        }
        viewModelScope.launch {
            repository.observeHealth().collect { health -> _state.update { it.copy(health = health) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshSystemIfStale()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false) }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (it.status == null) result.error else null,
                        writeError = if (it.status == null) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }
}
