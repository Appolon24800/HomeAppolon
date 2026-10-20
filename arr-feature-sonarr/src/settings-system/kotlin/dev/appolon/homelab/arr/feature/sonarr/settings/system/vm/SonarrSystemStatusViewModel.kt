package dev.appolon.homelab.arr.feature.sonarr.settings.system.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.sonarr.model.HealthCheckResult
import dev.appolon.homelab.arr.api.sonarr.model.HealthResource
import dev.appolon.homelab.arr.api.sonarr.model.SystemResource
import dev.appolon.homelab.arr.api.sonarr.model.TaskResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.feature.sonarr.settings.system.data.SonarrSettingsSystemRepository
import dev.appolon.homelab.arr.feature.sonarr.settings.system.data.SystemText
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One scheduled task with its cadence and runs pre-parsed for display:
 * the interval in minutes, the last run as an instant, and the last
 * duration in seconds (from the API's .NET time-span string).
 */
data class SonarrTaskRow(
    val task: TaskResource,
    val intervalMinutes: Int,
    val lastExecutionEpochMs: Long?,
    val lastDurationSeconds: Long?,
    val nextExecutionEpochMs: Long?,
)

/** Content payload of the system status LCE state. */
data class SonarrSystemStatusContent(
    val status: SystemResource,
    val tasks: List<SonarrTaskRow>,
    val diskSpace: List<DiskSpaceResource>,
    val health: List<HealthResource>,
    val refreshing: Boolean,
)

/**
 * System status screen state: host status card (version, runtime,
 * database type and migration state), scheduled tasks with interval and
 * last run, disk space bars per root path, and health checks with
 * wiki-link remedies. Read-only; loads cache-first with a TTL and
 * pull-to-refresh.
 */
class SonarrSystemStatusViewModel(
    private val repository: SonarrSettingsSystemRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val status: SystemResource = SystemResource(),
        val hasStatus: Boolean = false,
        val tasks: List<TaskResource> = emptyList(),
        val diskSpace: List<DiskSpaceResource> = emptyList(),
        val health: List<HealthResource> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SonarrSystemStatusContent>> = state
        .map { s ->
            when {
                s.loadError != null && !s.hasStatus -> LceState.Error(s.loadError)
                !s.loaded || !s.hasStatus -> LceState.Loading
                else -> LceState.Content(
                    SonarrSystemStatusContent(
                        status = s.status,
                        tasks = s.tasks.map { it.toRow() },
                        diskSpace = s.diskSpace,
                        health = s.health.sortedBy { healthRank(it.type) },
                        refreshing = s.refreshing,
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeSystemStatus().collect { status ->
                if (status != null) {
                    _state.update { it.copy(loaded = true, loadError = null, status = status, hasStatus = true) }
                }
            }
        }
        viewModelScope.launch {
            repository.observeTasks().collect { tasks -> _state.update { it.copy(tasks = tasks) } }
        }
        viewModelScope.launch {
            repository.observeDiskSpace().collect { disk -> _state.update { it.copy(diskSpace = disk) } }
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
                        loadError = if (!it.hasStatus) result.error else null,
                        refreshing = false,
                    )
                }
            }
        }
    }

    private fun TaskResource.toRow() = SonarrTaskRow(
        task = this,
        intervalMinutes = interval,
        lastExecutionEpochMs = ArrTime.parse(lastExecution)?.toEpochMilli(),
        lastDurationSeconds = SystemText.dotNetDurationSeconds(lastDuration),
        nextExecutionEpochMs = ArrTime.parse(nextExecution)?.toEpochMilli(),
    )

    private fun healthRank(type: HealthCheckResult?): Int = when (type) {
        HealthCheckResult.ERROR -> 0
        HealthCheckResult.WARNING -> 1
        HealthCheckResult.NOTICE -> 2
        else -> 3
    }
}
