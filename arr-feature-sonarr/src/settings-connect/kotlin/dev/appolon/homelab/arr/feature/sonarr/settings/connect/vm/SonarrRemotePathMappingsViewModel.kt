package dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.data.SonarrConnectRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editable mapping draft; a null id creates. */
data class RemotePathMappingDraft(
    val id: Int = 0,
    val host: String = "",
    val remotePath: String = "",
    val localPath: String = "",
) {
    val complete: Boolean get() = host.isNotBlank() && remotePath.isNotBlank() && localPath.isNotBlank()
}

/** Transient results of mapping writes, mapped to strings by the screen. */
enum class RemotePathMappingsNotice { SAVED, SAVED_QUEUED, DELETED, DELETED_QUEUED }

/** Remote path mappings CRUD state with an inline editor dialog. */
class SonarrRemotePathMappingsViewModel(
    private val serverId: ServerId,
    private val repository: SonarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val mappings: List<RemotePathMappingResource> = emptyList(),
        val pendingActions: Int = 0,
        val editorDraft: RemotePathMappingDraft? = null,
        val confirmDeleteFor: RemotePathMappingResource? = null,
        val writeError: ArrError? = null,
        val notice: RemotePathMappingsNotice? = null,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<UiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.mappings.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.mappings.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeRemotePathMappings().collect { mappings ->
                _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        mappings = mappings.sortedWith(
                            compareBy({ mapping -> mapping.host }, { mapping -> mapping.remotePath }),
                        ),
                    )
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

    fun startCreate() {
        _state.update { it.copy(editorDraft = RemotePathMappingDraft()) }
    }

    fun startEdit(mapping: RemotePathMappingResource) {
        _state.update {
            it.copy(
                editorDraft = RemotePathMappingDraft(
                    id = mapping.id,
                    host = mapping.host.orEmpty(),
                    remotePath = mapping.remotePath.orEmpty(),
                    localPath = mapping.localPath.orEmpty(),
                ),
            )
        }
    }

    fun updateDraft(transform: (RemotePathMappingDraft) -> RemotePathMappingDraft) {
        _state.update { s -> s.copy(editorDraft = s.editorDraft?.let(transform)) }
    }

    fun cancelEdit() {
        _state.update { it.copy(editorDraft = null) }
    }

    fun save() {
        val draft = _state.value.editorDraft ?: return
        if (!draft.complete) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (
                val saved = repository.saveRemotePathMapping(
                    RemotePathMappingResource(
                        id = draft.id,
                        host = draft.host.trim(),
                        remotePath = draft.remotePath.trim(),
                        localPath = draft.localPath.trim(),
                    ),
                )
            ) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        editorDraft = null,
                        writeError = null,
                        notice = if (saved.value is WriteOutcome.Queued) {
                            RemotePathMappingsNotice.SAVED_QUEUED
                        } else {
                            RemotePathMappingsNotice.SAVED
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, writeError = saved.error)
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
            when (val removed = repository.removeRemotePathMapping(target.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        notice = if (removed.value is WriteOutcome.Queued) {
                            RemotePathMappingsNotice.DELETED_QUEUED
                        } else {
                            RemotePathMappingsNotice.DELETED
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, writeError = removed.error)
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
