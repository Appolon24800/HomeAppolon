package dev.appolon.homelab.arr.feature.radarr.settings.system.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.radarr.model.HealthResource
import dev.appolon.homelab.arr.api.radarr.model.SystemResource
import dev.appolon.homelab.arr.api.radarr.model.TaskResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.radarr.settings.system.data.RadarrSettingsSystemRepository
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
data class RadarrTaskRow(
    val task: TaskResource,
    val intervalMinutes: Int,
    val lastExecutionEpochMs: Long?,
    val lastDurationSeconds: Long?,
    val nextExecutionEpochMs: Long?,
)

/** Content payload of the system status LCE state. */
data class RadarrSystemStatusContent(
    val status: SystemResource,
    val tasks: List<RadarrTaskRow>,
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
class RadarrSystemStatusViewModel(
    private val repository: RadarrSettingsSystemRepository,
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

    val lce: StateFlow<LceState<RadarrSystemStatusContent>> = state
        .map { s ->
            when {
                s.loadError != null && !s.hasStatus -> LceState.Error(s.loadError)
                !s.loaded || !s.hasStatus -> LceState.Loading
                else -> LceState.Content(
                    RadarrSystemStatusContent(
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

    private fun TaskResource.toRow() = RadarrTaskRow(
        task = this,
        intervalMinutes = interval,
        lastExecutionEpochMs = dev.appolon.homelab.arr.core.ArrTime.parse(lastExecution)?.toEpochMilli(),
        lastDurationSeconds = dev.appolon.homelab.arr.feature.radarr.settings.system.data.SystemText
            .dotNetDurationSeconds(lastDuration),
        nextExecutionEpochMs = dev.appolon.homelab.arr.core.ArrTime.parse(nextExecution)?.toEpochMilli(),
    )

    private fun healthRank(type: dev.appolon.homelab.arr.api.radarr.model.HealthCheckResult?): Int =
        when (type) {
            dev.appolon.homelab.arr.api.radarr.model.HealthCheckResult.ERROR -> 0
            dev.appolon.homelab.arr.api.radarr.model.HealthCheckResult.WARNING -> 1
            dev.appolon.homelab.arr.api.radarr.model.HealthCheckResult.NOTICE -> 2
            else -> 3
        }
}
