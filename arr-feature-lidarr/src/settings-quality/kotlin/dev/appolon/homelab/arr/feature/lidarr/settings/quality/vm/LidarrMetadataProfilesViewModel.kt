package dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfilePrimaryAlbumTypeItemResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfileReleaseStatusItemResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfileSecondaryAlbumTypeItemResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.data.LidarrQualityRepository
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
 * Editable draft of a metadata profile, the table Lidarr has and Sonarr
 * does not. Each row toggles one primary album type, secondary album type,
 * or release status; allowed rows are the album kinds Lidarr monitors for
 * artists on this profile.
 */
data class MetadataProfileDraft(
    val profileId: Int = 0,
    val name: String = "",
    val primaryAlbumTypes: List<ProfilePrimaryAlbumTypeItemResource> = emptyList(),
    val secondaryAlbumTypes: List<ProfileSecondaryAlbumTypeItemResource> = emptyList(),
    val releaseStatuses: List<ProfileReleaseStatusItemResource> = emptyList(),
    /** True when this draft edits an existing profile rather than the schema. */
    val isExisting: Boolean = false,
) {
    /** Counts of allowed rows, for the list's supporting text. */
    fun allowedCounts(): Triple<Int, Int, Int> = Triple(
        primaryAlbumTypes.count { it.allowed },
        secondaryAlbumTypes.count { it.allowed },
        releaseStatuses.count { it.allowed },
    )

    /** Builds the wire resource 1:1; toggling is the only edit this builder has. */
    fun toResource(): MetadataProfileResource = MetadataProfileResource(
        id = profileId,
        name = name,
        primaryAlbumTypes = primaryAlbumTypes,
        secondaryAlbumTypes = secondaryAlbumTypes,
        releaseStatuses = releaseStatuses,
    )

    companion object {
        /** Fills a draft from a loaded profile or the blank schema. */
        fun from(resource: MetadataProfileResource, isExisting: Boolean): MetadataProfileDraft =
            MetadataProfileDraft(
                profileId = resource.id,
                name = resource.name.orEmpty(),
                primaryAlbumTypes = resource.primaryAlbumTypes.orEmpty(),
                secondaryAlbumTypes = resource.secondaryAlbumTypes.orEmpty(),
                releaseStatuses = resource.releaseStatuses.orEmpty(),
                isExisting = isExisting,
            )
    }
}

/** Content payload of the metadata profiles list LCE state. */
data class LidarrMetadataProfilesContent(
    val profiles: List<MetadataProfileResource> = emptyList(),
    val pendingActions: Int = 0,
)

/** Content payload of the metadata profile builder LCE state. */
data class LidarrMetadataProfileEditorContent(
    val draft: MetadataProfileDraft = MetadataProfileDraft(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val pendingActions: Int = 0,
)

/** Transient list-screen results, mapped to strings by the screen. */
enum class MetadataProfilesNotice { CREATED, UPDATED, DELETED, QUEUED }

/**
 * Metadata profiles list screen state: cache-first rows, create through the
 * schema-driven builder, delete with confirmation. Writes queue offline
 * with optimistic cache updates.
 */
class LidarrMetadataProfilesViewModel(
    private val repository: LidarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val profiles: List<MetadataProfileResource> = emptyList(),
        val pendingActions: Int = 0,
        val notice: MetadataProfilesNotice? = null,
        val writeError: ArrError? = null,
        /** Profile id pending delete confirmation. */
        val confirmDeleteId: Int? = null,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrMetadataProfilesContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    LidarrMetadataProfilesContent(s.profiles.sortedBy { it.name }, s.pendingActions),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeMetadataProfiles().collect { profiles ->
                _state.update { it.copy(loaded = true, loadError = null, profiles = profiles) }
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
                        loadError = if (!it.loaded) result.error else null,
                        writeError = if (it.loaded) result.error else null,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun requestDelete(profileId: Int?) {
        _state.update { it.copy(confirmDeleteId = profileId) }
    }

    fun deleteConfirmed() {
        val id = _state.value.confirmDeleteId ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.deleteMetadataProfile(id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        confirmDeleteId = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            MetadataProfilesNotice.QUEUED
                        } else {
                            MetadataProfilesNotice.DELETED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, confirmDeleteId = null, writeError = result.error)
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

/**
 * Schema-driven metadata profile builder state. New profiles start from the
 * server schema (every album type and release status listed); existing ones
 * load from the cache. Saves POST for new profiles and PUT for existing ones.
 */
class LidarrMetadataProfileEditorViewModel(
    private val profileId: Int?,
    private val repository: LidarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val draft: MetadataProfileDraft = MetadataProfileDraft(),
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val pendingActions: Int = 0,
        val savedNotice: Boolean = false,
        val savedQueued: Boolean = false,
        val writeError: ArrError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrMetadataProfileEditorContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    LidarrMetadataProfileEditorContent(
                        draft = s.draft,
                        dirty = s.dirty,
                        saving = s.saving,
                        pendingActions = s.pendingActions,
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.queuedCount().collect { pending ->
                _state.update { it.copy(pendingActions = pending) }
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            repository.refreshIfStale()
            val existing = profileId?.let { id ->
                repository.cachedMetadataProfiles().firstOrNull { it.id == id }
            }
            if (existing != null) {
                _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        draft = MetadataProfileDraft.from(existing, isExisting = true),
                    )
                }
                return@launch
            }
            if (profileId != null) {
                // Editing a row that vanished (deleted on the server or a
                // stale link) surfaces as an error rather than a blank form.
                _state.update {
                    it.copy(loadError = ArrError.Http(404, null))
                }
                return@launch
            }
            when (val schema = repository.refreshMetadataProfileSchema()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        draft = MetadataProfileDraft.from(schema.value, isExisting = false),
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(loadError = schema.error) }
            }
        }
    }

    fun refresh() {
        load()
    }

    fun edit(transform: (MetadataProfileDraft) -> MetadataProfileDraft) {
        _state.update { it.copy(draft = transform(it.draft), dirty = true) }
    }

    fun setName(name: String) {
        edit { it.copy(name = name) }
    }

    /** Toggles one primary album type by its album type id. */
    fun togglePrimaryType(item: ProfilePrimaryAlbumTypeItemResource) {
        edit { draft ->
            draft.copy(
                primaryAlbumTypes = draft.primaryAlbumTypes.map {
                    if (it.albumType?.id == item.albumType?.id) it.copy(allowed = !it.allowed) else it
                },
            )
        }
    }

    /** Toggles one secondary album type by its album type id. */
    fun toggleSecondaryType(item: ProfileSecondaryAlbumTypeItemResource) {
        edit { draft ->
            draft.copy(
                secondaryAlbumTypes = draft.secondaryAlbumTypes.map {
                    if (it.albumType?.id == item.albumType?.id) it.copy(allowed = !it.allowed) else it
                },
            )
        }
    }

    /** Toggles one release status by its status id. */
    fun toggleReleaseStatus(item: ProfileReleaseStatusItemResource) {
        edit { draft ->
            draft.copy(
                releaseStatuses = draft.releaseStatuses.map {
                    if (it.releaseStatus?.id == item.releaseStatus?.id) {
                        it.copy(allowed = !it.allowed)
                    } else {
                        it
                    }
                },
            )
        }
    }

    fun save() {
        val current = _state.value
        if (!current.dirty || current.saving) return
        if (current.draft.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.saveMetadataProfile(current.draft.toResource())) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        savedNotice = result.value is WriteOutcome.Synced,
                        savedQueued = result.value is WriteOutcome.Queued,
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, writeError = result.error)
                }
            }
        }
    }

    fun consumeSavedNotice() {
        _state.update { it.copy(savedNotice = false, savedQueued = false) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }
}
