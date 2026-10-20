package dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.data.SonarrQualityRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Content payload of the delay profiles LCE state. */
data class SonarrDelayProfilesContent(
    val profiles: List<DelayProfileResource> = emptyList(),
    val dirtyIds: Set<Int> = emptySet(),
    val pendingActions: Int = 0,
)

/** Transient results, mapped to strings by the screen. */
enum class DelayProfilesNotice { CREATED, UPDATED, DELETED, MOVED, QUEUED }

/**
 * Delay profiles screen state: an ordered card list with inline editors for
 * the protocol-specific delays and both bypass rules, plus move up / move
 * down through the reorder endpoint. Saved rows keep local edit drafts so
 * typing never writes to the server until the row's save button is tapped.
 * New rows start with both protocols enabled. The untagged profile is
 * Sonarr's global fallback and cannot be deleted here.
 */
class SonarrDelayProfilesViewModel(
    private val repository: SonarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val profiles: List<DelayProfileResource> = emptyList(),
        /** Local edits of saved rows, keyed by profile id. */
        val edits: Map<Int, DelayProfileResource> = emptyMap(),
        /** Client-side drafts of newly added rows. */
        val newDrafts: List<DelayProfileResource> = emptyList(),
        val pendingActions: Int = 0,
        val busyId: Int? = null,
        val notice: DelayProfilesNotice? = null,
        val writeError: ArrError? = null,
        val confirmDeleteId: Int? = null,
    ) {
        /** Rows in display order: saved rows (or their local edits) plus new drafts. */
        fun displayed(): List<DelayProfileResource> =
            profiles.sortedBy { it.order }.map { edits[it.id] ?: it } + newDrafts

        /** Ids whose local draft differs from the saved row. */
        fun dirtyIds(): Set<Int> = edits.filterValues { edited ->
            val saved = profiles.firstOrNull { it.id == edited.id }
            saved == null || saved != edited
        }.keys
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SonarrDelayProfilesContent>> = combine(
        state,
        repository.queuedCount(),
    ) { s, pending ->
        s.copy(pendingActions = pending)
    }.map { s ->
        when {
            s.loadError != null -> LceState.Error(s.loadError)
            !s.loaded -> LceState.Loading
            else -> LceState.Content(
                SonarrDelayProfilesContent(
                    profiles = s.displayed(),
                    dirtyIds = s.dirtyIds(),
                    pendingActions = s.pendingActions,
                ),
                refreshing = s.refreshing,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeDelayProfiles().collect { profiles ->
                // Drop local edits for rows that disappeared or now match.
                _state.update { s ->
                    s.copy(
                        loaded = true,
                        loadError = null,
                        profiles = profiles,
                        edits = s.edits.filterValues { edited ->
                            profiles.any { it.id == edited.id && it != edited }
                        },
                    )
                }
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
                        loadError = if (!it.loaded) result.error else null,
                        writeError = if (it.loaded) result.error else null,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun addProfile() {
        _state.update { s ->
            s.copy(
                newDrafts = s.newDrafts + DelayProfileResource(
                    enableUsenet = true,
                    enableTorrent = true,
                    preferredProtocol = DownloadProtocol.USENET,
                ),
            )
        }
    }

    /** Applies an edit to a new draft or a saved row's local draft. */
    fun editDraft(
        profile: DelayProfileResource,
        transform: (DelayProfileResource) -> DelayProfileResource,
    ) {
        _state.update { s ->
            if (profile.id > 0) {
                val base = s.edits[profile.id] ?: s.profiles.firstOrNull { it.id == profile.id } ?: profile
                s.copy(edits = s.edits + (profile.id to transform(base)))
            } else {
                s.copy(newDrafts = s.newDrafts.map { if (it === profile) transform(it) else it })
            }
        }
    }

    /** Drops a new draft, or reverts a saved row's local edits. */
    fun discardDraft(profile: DelayProfileResource) {
        _state.update { s ->
            if (profile.id > 0) {
                s.copy(edits = s.edits - profile.id)
            } else {
                s.copy(newDrafts = s.newDrafts - profile)
            }
        }
    }

    /** Saves one row: POST for drafts, PUT for saved profiles. */
    fun save(profile: DelayProfileResource) {
        val isNew = profile.id <= 0
        viewModelScope.launch {
            _state.update { it.copy(busyId = profile.id) }
            when (val result = repository.saveDelayProfile(profile)) {
                is ArrResult.Ok -> _state.update { s ->
                    val queued = result.value is WriteOutcome.Queued
                    s.copy(
                        newDrafts = if (isNew && !queued) {
                            s.newDrafts - profile
                        } else {
                            s.newDrafts
                        },
                        edits = s.edits - profile.id,
                        busyId = null,
                        notice = when {
                            queued -> DelayProfilesNotice.QUEUED
                            isNew -> DelayProfilesNotice.CREATED
                            else -> DelayProfilesNotice.UPDATED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(busyId = null, writeError = result.error) }
            }
        }
    }

    fun moveUp(profile: DelayProfileResource) {
        val ordered = _state.value.profiles.sortedBy { it.order }
        val index = ordered.indexOfFirst { it.id == profile.id }
        if (index <= 0) return
        // The reorder endpoint places a row directly after its anchor, so
        // moving up means anchoring on the row above the predecessor.
        val afterId = if (index >= 2) ordered[index - 2].id else null
        move(profile.id, afterId)
    }

    fun moveDown(profile: DelayProfileResource) {
        val ordered = _state.value.profiles.sortedBy { it.order }
        val index = ordered.indexOfFirst { it.id == profile.id }
        if (index < 0 || index >= ordered.size - 1) return
        move(profile.id, ordered[index + 1].id)
    }

    private fun move(profileId: Int, afterId: Int?) {
        viewModelScope.launch {
            _state.update { it.copy(busyId = profileId) }
            when (val result = repository.moveDelayProfile(profileId, afterId)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busyId = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            DelayProfilesNotice.QUEUED
                        } else {
                            DelayProfilesNotice.MOVED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(busyId = null, writeError = result.error) }
            }
        }
    }

    fun requestDelete(profileId: Int?) {
        _state.update { it.copy(confirmDeleteId = profileId) }
    }

    fun deleteConfirmed() {
        val id = _state.value.confirmDeleteId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busyId = id) }
            when (val result = repository.deleteDelayProfile(id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busyId = null,
                        confirmDeleteId = null,
                        edits = it.edits - id,
                        notice = if (result.value is WriteOutcome.Queued) {
                            DelayProfilesNotice.QUEUED
                        } else {
                            DelayProfilesNotice.DELETED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busyId = null, confirmDeleteId = null, writeError = result.error)
                }
            }
        }
    }

    /** True for the untagged global fallback Sonarr keeps undeletable. */
    fun isDefaultProfile(profile: DelayProfileResource): Boolean =
        profile.id > 0 && profile.tags.isNullOrEmpty()

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }
}
