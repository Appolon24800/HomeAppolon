package dev.appolon.homelab.arr.feature.lidarr.settings.system.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.LogFileResource
import dev.appolon.homelab.arr.api.lidarr.model.SystemResource
import dev.appolon.homelab.arr.api.lidarr.model.UpdateResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.system.data.LidarrSettingsSystemRepository
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
 * One update package plus the derived flags the screen renders: the
 * installed row doubles as the current version card, installable rows offer
 * the install trigger.
 */
data class LidarrUpdateRow(
    val update: UpdateResource,
    val isCurrent: Boolean,
    val installable: Boolean,
)

/** Content payload of the updates LCE state. */
data class LidarrUpdatesContent(
    val status: SystemResource?,
    val updates: List<LidarrUpdateRow>,
    val logFiles: List<LogFileResource>,
    val checking: Boolean,
    val installing: Boolean,
    val pendingActions: Int,
)

/** Transient action results, mapped to strings by the screen. */
enum class UpdatesNotice { CHECKED, INSTALL_STARTED, INSTALL_QUEUED }

/**
 * Updates screen state: the current version card, the available update
 * list with an install trigger dispatched as the ApplicationUpdate
 * command, and the update log file viewer. Everything loads cache-first;
 * the install command queues for replay while offline.
 */
class LidarrUpdatesViewModel(
    private val repository: LidarrSettingsSystemRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val checking: Boolean = false,
        val installing: Boolean = false,
        val status: SystemResource? = null,
        val updates: List<UpdateResource> = emptyList(),
        val logFiles: List<LogFileResource> = emptyList(),
        val pendingActions: Int = 0,
        /** Log file open in the viewer, with its contents once loaded. */
        val openLogFile: LogFileResource? = null,
        val logContents: String? = null,
        val logLoading: Boolean = false,
        val logError: ArrError? = null,
        val notice: UpdatesNotice? = null,
        val writeError: ArrError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrUpdatesContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.updates.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.updates.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    LidarrUpdatesContent(
                        status = s.status,
                        updates = s.updates.map { it.toRow(updates = s.updates) },
                        logFiles = s.logFiles,
                        checking = s.checking,
                        installing = s.installing,
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeUpdates().collect { updates ->
                _state.update {
                    it.copy(loaded = true, loadError = null, updates = updates)
                }
            }
        }
        viewModelScope.launch {
            repository.observeSystemStatus().collect { status ->
                _state.update { it.copy(status = status) }
            }
        }
        viewModelScope.launch {
            repository.observeUpdateLogFiles().collect { files ->
                _state.update { it.copy(logFiles = files) }
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
            when (val result = repository.refreshUpdatesIfStale()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false) }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (it.updates.isEmpty()) result.error else null,
                        writeError = if (it.updates.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    /** Explicit update check; always bypasses the TTL. */
    fun checkForUpdates() {
        if (_state.value.checking) return
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            when (val result = repository.refreshUpdates()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(checking = false, notice = UpdatesNotice.CHECKED, writeError = null)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(checking = false, notice = null, writeError = result.error)
                }
            }
        }
    }

    /** Dispatches the ApplicationUpdate command; queued when offline. */
    fun install() {
        if (_state.value.installing) return
        viewModelScope.launch {
            _state.update { it.copy(installing = true) }
            when (val result = repository.installUpdate()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        installing = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            UpdatesNotice.INSTALL_QUEUED
                        } else {
                            UpdatesNotice.INSTALL_STARTED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(installing = false, notice = null, writeError = result.error)
                }
            }
        }
    }

    /** Opens one update log file's contents in the viewer. */
    fun openLogFile(file: LogFileResource) {
        val filename = file.filename ?: return
        viewModelScope.launch {
            _state.update { it.copy(openLogFile = file, logContents = null, logLoading = true, logError = null) }
            when (val result = repository.updateLogFileContents(filename)) {
                is ArrResult.Ok -> _state.update { it.copy(logLoading = false, logContents = result.value) }

                is ArrResult.Err -> _state.update {
                    it.copy(logLoading = false, logContents = null, logError = result.error)
                }
            }
        }
    }

    fun closeLogFile() {
        _state.update { it.copy(openLogFile = null, logContents = null, logError = null) }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }

    private fun UpdateResource.toRow(updates: List<UpdateResource>): LidarrUpdateRow {
        val current = updates.firstOrNull { it.installed && it.latest } ?: updates.firstOrNull { it.installed }
        return LidarrUpdateRow(
            update = this,
            isCurrent = current?.version == version,
            installable = installable && !installed,
        )
    }
}
