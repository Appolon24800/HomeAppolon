package dev.appolon.homelab.arr.feature.sonarr.settings.system.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.BackupResource
import dev.appolon.homelab.arr.api.sonarr.model.HostConfigResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.system.data.SonarrSettingsSystemRepository
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
 * Editable backup schedule from the host config: folder, interval, and
 * retention. Values live as strings; parsing happens at save time with a
 * fallback to the loaded numbers.
 */
data class BackupScheduleDraft(
    val folder: String = "",
    val intervalDays: String = "7",
    val retentionDays: String = "28",
) {
    constructor(config: HostConfigResource) : this(
        folder = config.backupFolder.orEmpty(),
        intervalDays = config.backupInterval.toString(),
        retentionDays = config.backupRetention.toString(),
    )

    fun parsedInterval(config: HostConfigResource): Int =
        intervalDays.toIntOrNull() ?: config.backupInterval

    fun parsedRetention(config: HostConfigResource): Int =
        retentionDays.toIntOrNull() ?: config.backupRetention

    /** True when a typed field is non-blank and not a number. */
    fun hasNumberError(): Boolean =
        (intervalDays.isNotBlank() && intervalDays.toIntOrNull() == null) ||
            (retentionDays.isNotBlank() && retentionDays.toIntOrNull() == null)
}

/** Content payload of the backup LCE state. */
data class SonarrBackupContent(
    val backups: List<BackupResource>,
    val schedule: BackupScheduleDraft,
    val scheduleDirty: Boolean,
    val scheduleSaving: Boolean,
    val scheduleError: Boolean,
    val busy: Boolean,
    val pendingActions: Int,
)

/** Transient action results, mapped to strings by the screen. */
enum class BackupNotice {
    CREATED,
    CREATED_QUEUED,
    RESTORED,
    DELETED,
    DELETED_QUEUED,
    SCHEDULE_SAVED,
    SCHEDULE_SAVED_QUEUED,
}

/**
 * Backup screen state: the backup list with schedule editing, create via
 * the Backup command, restore with confirmation, and delete. List and
 * schedule both load cache-first; offline writes queue with the pending
 * banner reflecting them.
 */
class SonarrBackupViewModel(
    private val repository: SonarrSettingsSystemRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val backups: List<BackupResource> = emptyList(),
        val schedule: BackupScheduleDraft = BackupScheduleDraft(),
        val scheduleDirty: Boolean = false,
        val scheduleSaving: Boolean = false,
        val busy: Boolean = false,
        val pendingActions: Int = 0,
        val confirmRestoreFor: BackupResource? = null,
        val confirmDeleteFor: BackupResource? = null,
        val notice: BackupNotice? = null,
        val writeError: ArrError? = null,
    )

    private var loadedConfig: HostConfigResource = HostConfigResource()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SonarrBackupContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.backups.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.backups.isEmpty() && !s.scheduleDirty -> LceState.Empty
                else -> LceState.Content(
                    SonarrBackupContent(
                        backups = s.backups,
                        schedule = s.schedule,
                        scheduleDirty = s.scheduleDirty,
                        scheduleSaving = s.scheduleSaving,
                        scheduleError = s.schedule.hasNumberError(),
                        busy = s.busy,
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeBackups().collect { backups ->
                _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        backups = backups.sortedByDescending { backup -> backup.time },
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeHostConfig().collect { config ->
                if (config != null) {
                    loadedConfig = config
                    _state.update { s ->
                        if (s.scheduleDirty) {
                            s.copy(loaded = true, loadError = null)
                        } else {
                            s.copy(loaded = true, loadError = null, schedule = BackupScheduleDraft(config))
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.queuedCount().collect { pending ->
                _state.update { it.copy(pendingActions = pending) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshBackupsIfStale()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false) }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (it.backups.isEmpty()) result.error else null,
                        writeError = if (it.backups.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    /** Dispatches the Backup command; queued when offline. */
    fun createBackup() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = repository.createBackup()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            BackupNotice.CREATED_QUEUED
                        } else {
                            BackupNotice.CREATED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, notice = null, writeError = result.error)
                }
            }
        }
    }

    fun requestRestore(backup: BackupResource) {
        _state.update { it.copy(confirmRestoreFor = backup) }
    }

    fun restoreCancelled() {
        _state.update { it.copy(confirmRestoreFor = null) }
    }

    fun restoreConfirmed() {
        val target = _state.value.confirmRestoreFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, confirmRestoreFor = null) }
            when (val result = repository.restoreBackup(target.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(busy = false, notice = BackupNotice.RESTORED, writeError = null)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, notice = null, writeError = result.error)
                }
            }
        }
    }

    fun requestDelete(backup: BackupResource) {
        _state.update { it.copy(confirmDeleteFor = backup) }
    }

    fun deleteCancelled() {
        _state.update { it.copy(confirmDeleteFor = null) }
    }

    fun deleteConfirmed() {
        val target = _state.value.confirmDeleteFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, confirmDeleteFor = null) }
            when (val result = repository.deleteBackup(target.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            BackupNotice.DELETED_QUEUED
                        } else {
                            BackupNotice.DELETED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, notice = null, writeError = result.error)
                }
            }
        }
    }

    fun editSchedule(transform: (BackupScheduleDraft) -> BackupScheduleDraft) {
        _state.update { it.copy(schedule = transform(it.schedule), scheduleDirty = true) }
    }

    fun saveSchedule() {
        val current = _state.value
        if (!current.scheduleDirty || current.scheduleSaving) return
        if (current.schedule.hasNumberError()) return
        viewModelScope.launch {
            _state.update { it.copy(scheduleSaving = true) }
            val draft = current.schedule
            val config = loadedConfig.copy(
                backupFolder = draft.folder.ifBlank { null },
                backupInterval = draft.parsedInterval(loadedConfig),
                backupRetention = draft.parsedRetention(loadedConfig),
            )
            when (val result = repository.saveHostConfig(config)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        scheduleSaving = false,
                        scheduleDirty = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            BackupNotice.SCHEDULE_SAVED_QUEUED
                        } else {
                            BackupNotice.SCHEDULE_SAVED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(scheduleSaving = false, notice = null, writeError = result.error)
                }
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }
}
