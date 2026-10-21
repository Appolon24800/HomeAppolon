package dev.appolon.homelab.arr.feature.radarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.connect.data.RadarrConnectRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Content payload of the remote path mappings LCE state. */
data class RadarrRemotePathMappingsContent(
    val mappings: List<RemotePathMappingResource>,
    val pendingActions: Int,
)

/** Transient results of mapping writes, mapped to strings by the screen. */
enum class RemotePathMappingNotice {
    SAVED,
    SAVED_QUEUED,
    DELETED,
    DELETED_QUEUED,
}

/** Draft of one mapping row: the host plus the two paths. */
data class RemotePathMappingDraft(
    val id: Int = 0,
    val host: String = "",
    val remotePath: String = "",
    val localPath: String = "",
)

/**
 * Remote path mappings CRUD. Every field is plain text, so the add and
 * edit dialogs share one draft; writes fall back to the offline queue
 * with optimistic cache updates and queued badges.
 */
class RadarrRemotePathMappingsViewModel(
    private val serverId: ServerId,
    private val repository: RadarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val mappings: List<RemotePathMappingResource> = emptyList(),
        val pendingActions: Int = 0,
        val editing: RemotePathMappingDraft? = null,
        val confirmDeleteFor: RemotePathMappingResource? = null,
        val writeError: ArrError? = null,
        val notice: RemotePathMappingNotice? = null,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrRemotePathMappingsContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.mappings.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.mappings.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    RadarrRemotePathMappingsContent(s.mappings, s.pendingActions),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeRemotePathMappings().collect { mappings ->
                _state.update { it.copy(loaded = true, loadError = null, mappings = mappings) }
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
                        loadError = if (it.mappings.isEmpty()) result.error else null,
                        writeError = if (it.mappings.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun startAdd() {
        _state.update { it.copy(editing = RemotePathMappingDraft()) }
    }

    fun startEdit(mapping: RemotePathMappingResource) {
        _state.update {
            it.copy(
                editing = RemotePathMappingDraft(
                    id = mapping.id,
                    host = mapping.host.orEmpty(),
                    remotePath = mapping.remotePath.orEmpty(),
                    localPath = mapping.localPath.orEmpty(),
                ),
            )
        }
    }

    fun updateDraft(transform: (RemotePathMappingDraft) -> RemotePathMappingDraft) {
        _state.update { s -> s.copy(editing = s.editing?.let(transform)) }
    }

    fun cancelEdit() {
        _state.update { it.copy(editing = null) }
    }

    fun save() {
        val draft = _state.value.editing ?: return
        if (draft.host.isBlank() || draft.remotePath.isBlank() || draft.localPath.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val resource = RemotePathMappingResource(
                id = draft.id,
                host = draft.host.trim(),
                remotePath = draft.remotePath.trim(),
                localPath = draft.localPath.trim(),
            )
            when (val result = repository.saveRemotePathMapping(resource)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        editing = null,
                        writeError = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            RemotePathMappingNotice.SAVED_QUEUED
                        } else {
                            RemotePathMappingNotice.SAVED
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, writeError = result.error)
                }
            }
        }
    }

    fun requestDelete(mapping: RemotePathMappingResource) {
        _state.update { it.copy(confirmDeleteFor = mapping) }
    }

    fun deleteCancelled() {
        _state.update { it.copy(confirmDeleteFor = null) }
    }

    fun deleteConfirmed() {
        val target = _state.value.confirmDeleteFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, confirmDeleteFor = null) }
            when (val result = repository.removeRemotePathMapping(target.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            RemotePathMappingNotice.DELETED_QUEUED
                        } else {
                            RemotePathMappingNotice.DELETED
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, writeError = result.error)
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
