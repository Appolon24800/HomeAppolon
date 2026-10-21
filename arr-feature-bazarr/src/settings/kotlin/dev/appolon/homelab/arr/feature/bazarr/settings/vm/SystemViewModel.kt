package dev.appolon.homelab.arr.feature.bazarr.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.settings.data.BazarrSettingsRepository
import dev.appolon.homelab.arr.feature.bazarr.settings.data.SettingsWriteOutcome
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrBackupFile
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrJobRow
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrLogEntry
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

/** Which system feed is showing. */
enum class SystemTab { STATUS, JOBS, BACKUPS, LOGS }

/** Transient one-line result of a system write, surfaced as a snackbar. */
sealed interface SystemMessage {
    data object JobUpdatedSynced : SystemMessage
    data object JobUpdatedQueued : SystemMessage
    data object JobDeletedSynced : SystemMessage
    data object JobDeletedQueued : SystemMessage
    data object QueueEmptiedSynced : SystemMessage
    data object QueueEmptiedQueued : SystemMessage
    data object BackupCreatedSynced : SystemMessage
    data object BackupCreatedQueued : SystemMessage
    data object BackupRestoredSynced : SystemMessage
    data object BackupRestoredQueued : SystemMessage
    data object BackupDeletedSynced : SystemMessage
    data object BackupDeletedQueued : SystemMessage
    data object LogsRotatedSynced : SystemMessage
    data object LogsRotatedQueued : SystemMessage
    data class WriteFailed(val error: ArrError) : SystemMessage
}

/** State of the system screen: status rows, job queue, backups, and logs. */
data class SystemUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val tab: SystemTab = SystemTab.STATUS,
    val statusRows: List<BazarrStatusRow> = emptyList(),
    val jobs: List<BazarrJobRow> = emptyList(),
    val backups: List<BazarrBackupFile> = emptyList(),
    val logs: List<BazarrLogEntry> = emptyList(),
    val logTypeFilter: String? = null,
    val busyJobIds: Set<Int> = emptySet(),
    val queuedJobIds: Set<Int> = emptySet(),
    val queuedBackupFiles: Set<String> = emptySet(),
    val queuedLogRotation: Boolean = false,
    val refreshing: Boolean = false,
    val message: SystemMessage? = null,
) {
    /** Rows of the visible tab's log filter. */
    val presentedLogs: List<BazarrLogEntry>
        get() = logTypeFilter?.let { filter ->
            logs.filter { row -> row.type.equals(filter, ignoreCase = true) }
        } ?: logs

    /** Log levels present in the cache, for the filter chips. */
    val logTypes: List<String>
        get() = logs.map { it.type }.filter { it.isNotBlank() }.distinct().sorted()

    /** True when any feed carries data; keeps stale content on refresh errors. */
    val hasData: Boolean
        get() = statusRows.isNotEmpty() || jobs.isNotEmpty() ||
            backups.isNotEmpty() || logs.isNotEmpty()
}

/**
 * ViewModel of the system screen. All four feeds load through their cache
 * TTLs; writes hit the server first and queue offline with optimistic cache
 * updates, so job removals, backup deletions, and log rotation apply
 * immediately and show the queued indicator until replay.
 */
class SystemViewModel(
    private val repository: BazarrSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SystemUiState())
    val state: StateFlow<SystemUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SystemUiState>> = state
        .map { s ->
            when {
                s.loadError != null && !s.hasData -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                !s.hasData -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeStatus()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { rows -> _state.update { it.copy(statusRows = rows, loaded = true) } }
        }
        viewModelScope.launch {
            repository.observeJobs()
                .catch { }
                .collect { rows -> _state.update { it.copy(jobs = rows, loaded = true) } }
        }
        viewModelScope.launch {
            repository.observeBackups()
                .catch { }
                .collect { rows -> _state.update { it.copy(backups = rows, loaded = true) } }
        }
        viewModelScope.launch {
            repository.observeLogs()
                .catch { }
                .collect { rows -> _state.update { it.copy(logs = rows, loaded = true) } }
        }
        viewModelScope.launch {
            repository.queuedJobIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedJobIds = ids) } }
        }
        viewModelScope.launch {
            repository.queuedBackupFiles()
                .catch { }
                .collect { files -> _state.update { it.copy(queuedBackupFiles = files) } }
        }
        viewModelScope.launch {
            repository.queuedLogRotation()
                .catch { }
                .collect { queued -> _state.update { it.copy(queuedLogRotation = queued) } }
        }
        loadAll(force = false)
    }

    private fun loadAll(force: Boolean) {
        viewModelScope.launch {
            val ttl = if (force) {
                BazarrSettingsRepository.FORCE_REFRESH
            } else {
                BazarrSettingsRepository.STATUS_TTL_MILLIS
            }
            val volatileTtl = if (force) {
                BazarrSettingsRepository.FORCE_REFRESH
            } else {
                BazarrSettingsRepository.VOLATILE_TTL_MILLIS
            }
            val errors = mutableListOf<ArrError>()
            repository.status(ttl).errorInto(errors)
            repository.jobs(volatileTtl).errorInto(errors)
            repository.backups(ttl).errorInto(errors)
            repository.logs(volatileTtl).errorInto(errors)
            _state.update { current -> current.copy(loadError = errors.firstOrNull()) }
        }
    }

    /** Pull-to-refresh and error retry entry point; bypasses every TTL. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val errors = mutableListOf<ArrError>()
            repository.status(BazarrSettingsRepository.FORCE_REFRESH).errorInto(errors)
            repository.jobs(BazarrSettingsRepository.FORCE_REFRESH).errorInto(errors)
            repository.backups(BazarrSettingsRepository.FORCE_REFRESH).errorInto(errors)
            repository.logs(BazarrSettingsRepository.FORCE_REFRESH).errorInto(errors)
            _state.update {
                it.copy(refreshing = false, loadError = errors.firstOrNull())
            }
        }
    }

    fun setTab(tab: SystemTab) = _state.update { it.copy(tab = tab) }

    /** null selects every log level. */
    fun setLogTypeFilter(type: String?) = _state.update { it.copy(logTypeFilter = type) }

    /** Force starts, or moves a job to the top or bottom of its queue. */
    fun jobAction(id: Int, action: String) {
        viewModelScope.launch {
            _state.update { it.copy(busyJobIds = it.busyJobIds + id) }
            val message = when (val result = repository.jobAction(id, action)) {
                is ArrResult.Ok -> when (result.value) {
                    is SettingsWriteOutcome.Synced -> SystemMessage.JobUpdatedSynced
                    is SettingsWriteOutcome.Queued -> SystemMessage.JobUpdatedQueued
                }

                is ArrResult.Err -> SystemMessage.WriteFailed(result.error)
            }
            _state.update { it.copy(busyJobIds = it.busyJobIds - id, message = message) }
        }
    }

    /** Removes one job from the queue. */
    fun deleteJob(job: BazarrJobRow) {
        viewModelScope.launch {
            _state.update { it.copy(busyJobIds = it.busyJobIds + job.id) }
            val message = when (val result = repository.deleteJob(job)) {
                is ArrResult.Ok -> when (result.value) {
                    is SettingsWriteOutcome.Synced -> SystemMessage.JobDeletedSynced
                    is SettingsWriteOutcome.Queued -> SystemMessage.JobDeletedQueued
                }

                is ArrResult.Err -> SystemMessage.WriteFailed(result.error)
            }
            _state.update { it.copy(busyJobIds = it.busyJobIds - job.id, message = message) }
        }
    }

    /** Empties every pending job of one queue. */
    fun emptyQueue(queueName: String) {
        viewModelScope.launch {
            val message = when (val result = repository.emptyJobQueue(queueName)) {
                is ArrResult.Ok -> when (result.value) {
                    is SettingsWriteOutcome.Synced -> SystemMessage.QueueEmptiedSynced
                    is SettingsWriteOutcome.Queued -> SystemMessage.QueueEmptiedQueued
                }

                is ArrResult.Err -> SystemMessage.WriteFailed(result.error)
            }
            _state.update { it.copy(message = message) }
        }
    }

    /** Starts a new backup on the server. */
    fun createBackup() {
        viewModelScope.launch {
            val message = when (val result = repository.createBackup()) {
                is ArrResult.Ok -> when (result.value) {
                    is SettingsWriteOutcome.Synced -> SystemMessage.BackupCreatedSynced
                    is SettingsWriteOutcome.Queued -> SystemMessage.BackupCreatedQueued
                }

                is ArrResult.Err -> SystemMessage.WriteFailed(result.error)
            }
            _state.update { it.copy(message = message) }
        }
    }

    /** Restores one backup file. */
    fun restoreBackup(backup: BazarrBackupFile) {
        viewModelScope.launch {
            val message = when (val result = repository.restoreBackup(backup.filename)) {
                is ArrResult.Ok -> when (result.value) {
                    is SettingsWriteOutcome.Synced -> SystemMessage.BackupRestoredSynced
                    is SettingsWriteOutcome.Queued -> SystemMessage.BackupRestoredQueued
                }

                is ArrResult.Err -> SystemMessage.WriteFailed(result.error)
            }
            _state.update { it.copy(message = message) }
        }
    }

    /** Deletes one backup file. */
    fun deleteBackup(backup: BazarrBackupFile) {
        viewModelScope.launch {
            val message = when (val result = repository.deleteBackup(backup)) {
                is ArrResult.Ok -> when (result.value) {
                    is SettingsWriteOutcome.Synced -> SystemMessage.BackupDeletedSynced
                    is SettingsWriteOutcome.Queued -> SystemMessage.BackupDeletedQueued
                }

                is ArrResult.Err -> SystemMessage.WriteFailed(result.error)
            }
            _state.update { it.copy(message = message) }
        }
    }

    /** Closes the current log file and starts a new one. */
    fun rotateLogs() {
        viewModelScope.launch {
            val message = when (val result = repository.rotateLogs()) {
                is ArrResult.Ok -> when (result.value) {
                    is SettingsWriteOutcome.Synced -> SystemMessage.LogsRotatedSynced
                    is SettingsWriteOutcome.Queued -> SystemMessage.LogsRotatedQueued
                }

                is ArrResult.Err -> SystemMessage.WriteFailed(result.error)
            }
            _state.update { it.copy(message = message) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun ArrResult<Unit>.errorInto(errors: MutableList<ArrError>) {
        if (this is ArrResult.Err) errors.add(error)
    }
}
