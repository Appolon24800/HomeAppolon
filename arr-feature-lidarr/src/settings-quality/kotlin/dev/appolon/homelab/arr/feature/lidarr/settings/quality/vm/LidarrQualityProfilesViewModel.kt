package dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
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
 * One quality row in the profile builder. Group rows (qualities the server
 * nests under one entry) expose their children; toggling a group toggles
 * every child, mirroring Lidarr's group behavior.
 */
data class ProfileQualityItem(
    val id: Int,
    val name: String,
    val qualityId: Int?,
    val allowed: Boolean,
    val children: List<ProfileQualityItem> = emptyList(),
) {
    /** Leaf rows are selectable as the upgrade-until quality. */
    val isLeaf: Boolean get() = qualityId != null && children.isEmpty()

    /** A group counts as allowed when any child is allowed. */
    val effectivelyAllowed: Boolean get() = if (children.isEmpty()) allowed else children.any { it.allowed }
}

/**
 * Editable draft of a quality profile. Score fields are strings so partial
 * input never crashes; parsing happens at save time. Lidarr's profile has
 * no minimum upgrade score field; upgrades stop at the cutoff format score.
 */
data class QualityProfileDraft(
    val profileId: Int = 0,
    val name: String = "",
    val upgradeAllowed: Boolean = true,
    /** The quality id upgrades stop at; only allowed leaves are valid. */
    val cutoff: Int = 0,
    val items: List<ProfileQualityItem> = emptyList(),
    val minFormatScore: String = "0",
    val cutoffFormatScore: String = "0",
    val formatItems: List<ProfileFormatItemResource> = emptyList(),
    /** True when this draft edits an existing profile rather than the schema. */
    val isExisting: Boolean = false,
) {
    /** Leaf qualities currently allowed, in display order; the cutoff picker lists these. */
    fun allowedLeaves(): List<ProfileQualityItem> = items.flatMap(::leaves).filter { it.allowed }

    private fun leaves(item: ProfileQualityItem): List<ProfileQualityItem> =
        if (item.children.isEmpty()) listOf(item) else item.children.flatMap(::leaves)

    /**
     * Builds the wire resource. The cutoff falls back to the first allowed
     * leaf when the chosen one is no longer allowed, because the server
     * rejects cutoffs outside the allowed set.
     */
    fun toResource(): QualityProfileResource {
        val allowedIds = allowedLeaves().mapNotNull { it.qualityId }.toSet()
        val effectiveCutoff = when {
            cutoff in allowedIds -> cutoff
            else -> allowedLeaves().firstNotNullOfOrNull { it.qualityId } ?: 0
        }
        return QualityProfileResource(
            id = profileId,
            name = name,
            upgradeAllowed = upgradeAllowed,
            cutoff = effectiveCutoff,
            items = items.map(::itemToWire),
            minFormatScore = minFormatScore.toIntOrNull() ?: 0,
            cutoffFormatScore = cutoffFormatScore.toIntOrNull() ?: 0,
            formatItems = formatItems,
        )
    }

    private fun itemToWire(item: ProfileQualityItem): QualityProfileQualityItemResource =
        QualityProfileQualityItemResource(
            id = item.id,
            name = item.name,
            quality = item.qualityId?.let { Quality(id = it, name = item.name) },
            allowed = item.allowed,
            items = item.children.takeIf { it.isNotEmpty() }?.map(::itemToWire),
        )

    companion object {
        /** Fills a draft from a loaded profile or the blank schema. */
        fun from(resource: QualityProfileResource, isExisting: Boolean): QualityProfileDraft =
            QualityProfileDraft(
                profileId = resource.id,
                name = resource.name.orEmpty(),
                upgradeAllowed = resource.upgradeAllowed,
                cutoff = resource.cutoff,
                items = resource.items.orEmpty().map(::itemFromWire),
                minFormatScore = resource.minFormatScore.toString(),
                cutoffFormatScore = resource.cutoffFormatScore.toString(),
                formatItems = resource.formatItems.orEmpty(),
                isExisting = isExisting,
            )

        private fun itemFromWire(item: QualityProfileQualityItemResource): ProfileQualityItem =
            ProfileQualityItem(
                id = item.id,
                name = item.name ?: item.quality?.name.orEmpty(),
                qualityId = item.quality?.id,
                allowed = item.allowed,
                children = item.items.orEmpty().map(::itemFromWire),
            )
    }
}

/** Content payload of the quality profiles list LCE state. */
data class LidarrQualityProfilesContent(
    val profiles: List<QualityProfileResource> = emptyList(),
    val pendingActions: Int = 0,
)

/** Content payload of the profile builder LCE state. */
data class LidarrQualityProfileEditorContent(
    val draft: QualityProfileDraft = QualityProfileDraft(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val pendingActions: Int = 0,
)

/** Transient list-screen results, mapped to strings by the screen. */
enum class QualityProfilesNotice { CREATED, UPDATED, DELETED, QUEUED }

/**
 * Quality profiles list screen state: cache-first rows, create through the
 * schema-driven builder, delete with confirmation. Writes queue offline
 * with optimistic cache updates.
 */
class LidarrQualityProfilesViewModel(
    private val repository: LidarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val profiles: List<QualityProfileResource> = emptyList(),
        val pendingActions: Int = 0,
        val notice: QualityProfilesNotice? = null,
        val writeError: ArrError? = null,
        /** Profile id pending delete confirmation. */
        val confirmDeleteId: Int? = null,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrQualityProfilesContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    LidarrQualityProfilesContent(s.profiles.sortedBy { it.name }, s.pendingActions),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeQualityProfiles().collect { profiles ->
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
            when (val result = repository.deleteQualityProfile(id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        confirmDeleteId = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            QualityProfilesNotice.QUEUED
                        } else {
                            QualityProfilesNotice.DELETED
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
 * Schema-driven quality profile builder state. New profiles start from the
 * server schema (every quality listed, none allowed); existing ones load
 * from the cache. Saves POST for new profiles and PUT for existing ones.
 */
class LidarrQualityProfileEditorViewModel(
    private val profileId: Int?,
    private val repository: LidarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val draft: QualityProfileDraft = QualityProfileDraft(),
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val pendingActions: Int = 0,
        val savedNotice: Boolean = false,
        val savedQueued: Boolean = false,
        val writeError: ArrError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrQualityProfileEditorContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    LidarrQualityProfileEditorContent(
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
                repository.cachedQualityProfiles().firstOrNull { it.id == id }
            }
            if (existing != null) {
                _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        draft = QualityProfileDraft.from(existing, isExisting = true),
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
            when (val schema = repository.refreshQualityProfileSchema()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        draft = QualityProfileDraft.from(schema.value, isExisting = false),
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(loadError = schema.error) }
            }
        }
    }

    fun refresh() {
        load()
    }

    fun edit(transform: (QualityProfileDraft) -> QualityProfileDraft) {
        _state.update { it.copy(draft = transform(it.draft), dirty = true) }
    }

    /** Toggles one item; group rows carry the change to every child. */
    fun toggleAllowed(item: ProfileQualityItem) {
        edit { draft ->
            draft.copy(items = draft.items.map { root -> toggleIn(root, item.id, !item.allowed) })
        }
    }

    private fun toggleIn(node: ProfileQualityItem, targetId: Int, allowed: Boolean): ProfileQualityItem =
        if (node.id == targetId && node.children.isEmpty()) {
            node.copy(allowed = allowed)
        } else if (node.id == targetId) {
            node.copy(allowed = allowed, children = node.children.map { it.copy(allowed = allowed) })
        } else {
            node.copy(children = node.children.map { toggleIn(it, targetId, allowed) })
        }

    fun setCutoff(qualityId: Int) {
        edit { it.copy(cutoff = qualityId) }
    }

    fun setFormatScore(formatId: Int, score: String) {
        edit { draft ->
            draft.copy(
                formatItems = draft.formatItems.map { item ->
                    if (item.format == formatId) item.copy(score = score.toIntOrNull() ?: 0) else item
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
            when (val result = repository.saveQualityProfile(current.draft.toResource())) {
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
