package dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.data.LidarrConnectRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** New-exclusion dialog draft: an artist identified by its MusicBrainz id. */
data class ExclusionDraft(
    val foreignId: String = "",
    val artistName: String = "",
) {
    val complete: Boolean get() = artistName.isNotBlank() && foreignId.isNotBlank()
}

/** Transient results of exclusion writes, mapped to strings by the screen. */
enum class ExclusionsNotice { ADDED, ADDED_QUEUED, DELETED, DELETED_QUEUED }

/**
 * Import list exclusions state: rows keyed by MusicBrainz artist id, an
 * add dialog, and delete with confirm. Excluded artists never come back
 * from a list sync.
 */
class LidarrImportListExclusionsViewModel(
    private val serverId: ServerId,
    private val repository: LidarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val exclusions: List<ImportListExclusionResource> = emptyList(),
        val pendingActions: Int = 0,
        val addDraft: ExclusionDraft? = null,
        val confirmDeleteFor: ImportListExclusionResource? = null,
        val writeError: ArrError? = null,
        val notice: ExclusionsNotice? = null,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<UiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.exclusions.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.exclusions.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeExclusions().collect { exclusions ->
                _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        exclusions = exclusions.sortedBy { exclusion -> exclusion.artistName },
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
                        loadError = if (it.exclusions.isEmpty()) result.error else null,
                        writeError = if (it.exclusions.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun startAdd() {
        _state.update { it.copy(addDraft = ExclusionDraft()) }
    }

    fun updateDraft(transform: (ExclusionDraft) -> ExclusionDraft) {
        _state.update { s -> s.copy(addDraft = s.addDraft?.let(transform)) }
    }

    fun cancelAdd() {
        _state.update { it.copy(addDraft = null) }
    }

    fun add() {
        val draft = _state.value.addDraft ?: return
        if (!draft.complete) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (
                val added = repository.addExclusion(
                    ImportListExclusionResource(
                        foreignId = draft.foreignId.trim(),
                        artistName = draft.artistName.trim(),
                    ),
                )
            ) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        addDraft = null,
                        writeError = null,
                        notice = if (added.value is WriteOutcome.Queued) {
                            ExclusionsNotice.ADDED_QUEUED
                        } else {
                            ExclusionsNotice.ADDED
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, writeError = added.error)
                }
            }
        }
    }

    fun requestDelete(exclusion: ImportListExclusionResource) {
        _state.update { it.copy(confirmDeleteFor = exclusion) }
    }

    fun deleteCancelled() {
        _state.update { it.copy(confirmDeleteFor = null) }
    }

    fun deleteConfirmed() {
        val target = _state.value.confirmDeleteFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, confirmDeleteFor = null) }
            when (val removed = repository.removeExclusion(target)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        notice = if (removed.value is WriteOutcome.Queued) {
                            ExclusionsNotice.DELETED_QUEUED
                        } else {
                            ExclusionsNotice.DELETED
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
