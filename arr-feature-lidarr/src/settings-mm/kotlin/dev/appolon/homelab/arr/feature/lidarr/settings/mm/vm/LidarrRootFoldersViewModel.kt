package dev.appolon.homelab.arr.feature.lidarr.settings.mm.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.data.LidarrRootFolderRepository
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.data.RootFolderWithDisk
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Content payload of the root folders LCE state. */
data class LidarrRootFoldersContent(
    val folders: List<RootFolderWithDisk>,
    val queuedRemovedIds: Set<Int>,
    val pendingActions: Int,
)

/** Transient results of folder writes, mapped to strings by the screen. */
enum class RootFolderNotice {
    ADDED,
    ADDED_QUEUED,
    EDITED,
    EDITED_QUEUED,
    REMOVED,
    REMOVED_QUEUED,
}

/**
 * Root folders screen state: the cache-joined folder/disk list, per-row
 * queued badges for pending removals, and add/edit/remove writes that
 * fall back to the offline queue. The detail pane selection is plain UI
 * state held here so it survives rotation.
 */
class LidarrRootFoldersViewModel(
    private val serverId: ServerId,
    private val repository: LidarrRootFolderRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val folders: List<RootFolderWithDisk> = emptyList(),
        val queuedRemovedIds: Set<Int> = emptySet(),
        val pendingActions: Int = 0,
        val selectedFolderId: Int? = null,
        val writeError: ArrError? = null,
        val notice: RootFolderNotice? = null,
        val busy: Boolean = false,
    ) {
        val selected: RootFolderWithDisk?
            get() = folders.firstOrNull { it.folder.id == selectedFolderId }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrRootFoldersContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.folders.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.folders.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    LidarrRootFoldersContent(
                        folders = s.folders,
                        queuedRemovedIds = s.queuedRemovedIds,
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeWithDisk().collect { folders ->
                _state.update { s ->
                    s.copy(
                        loaded = true,
                        loadError = null,
                        folders = folders,
                        selectedFolderId = s.selectedFolderId
                            ?.takeIf { id -> folders.any { it.folder.id == id } },
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.queuedRemovedIds().collect { ids ->
                _state.update { it.copy(queuedRemovedIds = ids) }
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
            when (val result = repository.refreshIfStale()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false) }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (it.folders.isEmpty()) result.error else null,
                        writeError = if (it.folders.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun select(folderId: Int?) {
        _state.update { it.copy(selectedFolderId = folderId) }
    }

    fun add(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            applyWrite(repository.add(path.trim())) { queued ->
                if (queued) RootFolderNotice.ADDED_QUEUED else RootFolderNotice.ADDED
            }
        }
    }

    fun remove(folderId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            applyWrite(repository.remove(folderId)) { queued ->
                if (queued) RootFolderNotice.REMOVED_QUEUED else RootFolderNotice.REMOVED
            }
        }
    }

    /** Path edit: Lidarr's real PUT update, one request both online and queued. */
    fun move(folder: RootFolderResource, newPath: String) {
        if (newPath.isBlank() || newPath == folder.path) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            applyWrite(repository.move(folder, newPath.trim())) { queued ->
                if (queued) RootFolderNotice.EDITED_QUEUED else RootFolderNotice.EDITED
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }

    private fun applyWrite(
        result: ArrResult<WriteOutcome>,
        notice: (queued: Boolean) -> RootFolderNotice,
    ) {
        when (result) {
            is ArrResult.Ok -> _state.update {
                it.copy(busy = false, notice = notice(result.value is WriteOutcome.Queued), writeError = null)
            }

            is ArrResult.Err -> _state.update {
                it.copy(busy = false, notice = null, writeError = result.error)
            }
        }
    }
}
