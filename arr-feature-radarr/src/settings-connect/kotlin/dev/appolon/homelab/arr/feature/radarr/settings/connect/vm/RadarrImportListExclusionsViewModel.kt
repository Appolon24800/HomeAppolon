package dev.appolon.homelab.arr.feature.radarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionResource
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

/** Content payload of the exclusions LCE state. */
data class RadarrImportListExclusionsContent(
    val exclusions: List<ImportListExclusionResource>,
    val queuedRemovedIds: Set<Int>,
    val pendingActions: Int,
)

/** Transient results of exclusion writes, mapped to strings by the screen. */
enum class ImportListExclusionNotice {
    ADDED,
    ADDED_QUEUED,
    DELETED,
    DELETED_QUEUED,
}

/** Manual exclusion draft: the movie's TMDb id, title, and year. */
data class ImportListExclusionDraft(
    val id: Int = 0,
    val tmdbId: Int = 0,
    val title: String = "",
    val year: Int = 0,
) {
    val valid: Boolean get() = title.isNotBlank() && tmdbId > 0
}

/**
 * Import list exclusions CRUD. Rows come from the exclusions table; add
 * takes the TMDb id, title, and year, matching Radarr's manual exclusion
 * form. Writes fall back to the offline queue with queued badges.
 */
class RadarrImportListExclusionsViewModel(
    private val serverId: ServerId,
    private val repository: RadarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val exclusions: List<ImportListExclusionResource> = emptyList(),
        val queuedRemovedIds: Set<Int> = emptySet(),
        val pendingActions: Int = 0,
        val adding: ImportListExclusionDraft? = null,
        val confirmDeleteFor: ImportListExclusionResource? = null,
        val writeError: ArrError? = null,
        val notice: ImportListExclusionNotice? = null,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrImportListExclusionsContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.exclusions.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.exclusions.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    RadarrImportListExclusionsContent(
                        s.exclusions,
                        s.queuedRemovedIds,
                        s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeExclusions().collect { exclusions ->
                _state.update {
                    it.copy(loaded = true, loadError = null, exclusions = exclusions)
                }
            }
        }
        viewModelScope.launch {
            repository.queuedRemovedIds(RadarrConnectRepository.EXCLUSION_PATH).collect { ids ->
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
                        loadError = if (it.exclusions.isEmpty()) result.error else null,
                        writeError = if (it.exclusions.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun startAdd() {
        _state.update { it.copy(adding = ImportListExclusionDraft()) }
    }

    fun updateDraft(transform: (ImportListExclusionDraft) -> ImportListExclusionDraft) {
        _state.update { s -> s.copy(adding = s.adding?.let(transform)) }
    }

    fun cancelAdd() {
        _state.update { it.copy(adding = null) }
    }

    fun save() {
        val draft = _state.value.adding ?: return
        if (!draft.valid) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val resource = ImportListExclusionResource(
                tmdbId = draft.tmdbId,
                movieTitle = draft.title.trim(),
                movieYear = draft.year,
            )
            when (val result = repository.addExclusion(resource)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        adding = null,
                        writeError = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            ImportListExclusionNotice.ADDED_QUEUED
                        } else {
                            ImportListExclusionNotice.ADDED
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, writeError = result.error)
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
            when (val result = repository.removeExclusion(target.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            ImportListExclusionNotice.DELETED_QUEUED
                        } else {
                            ImportListExclusionNotice.DELETED
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
