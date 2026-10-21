package dev.appolon.homelab.arr.feature.radarr.settings.system.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.system.data.RadarrSettingsSystemRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One tag with its usage counts derived from TagDetails. */
data class RadarrTagRow(
    val tag: TagResource,
    val label: String,
    val queuedForRemoval: Boolean,
    val movieCount: Int,
    val importListCount: Int,
    val notificationCount: Int,
    val releaseProfileCount: Int,
    val indexerCount: Int,
    val downloadClientCount: Int,
    val delayProfileCount: Int,
    val autoTagCount: Int,
) {
    val used: Boolean get() = movieCount + importListCount + notificationCount + releaseProfileCount +
        indexerCount + downloadClientCount + delayProfileCount + autoTagCount > 0
}

/** Content payload of the tags LCE state. */
data class RadarrTagsContent(
    val rows: List<RadarrTagRow>,
    val pendingActions: Int,
)

/** Transient action results, mapped to strings by the screen. */
enum class TagsNotice { CREATED, CREATED_QUEUED, RENAMED, RENAMED_QUEUED, DELETED, DELETED_QUEUED }

/**
 * Tags screen state: the tag list with per-tag usage from TagDetails,
 * create and rename through a dialog, and delete with confirmation.
 * Offline writes apply optimistically and queue for replay; rows with a
 * queued removal keep rendering with a queued badge.
 */
class RadarrTagsViewModel(
    private val repository: RadarrSettingsSystemRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val tags: List<TagResource> = emptyList(),
        val detailsById: Map<Int, TagDetailsResource> = emptyMap(),
        val queuedRemovedIds: Set<Int> = emptySet(),
        val pendingActions: Int = 0,
        /** Editor dialog: null id means creating. */
        val editorOpen: Boolean = false,
        val editingTagId: Int? = null,
        val editingLabel: String = "",
        val confirmDeleteFor: TagResource? = null,
        val busy: Boolean = false,
        val notice: TagsNotice? = null,
        val writeError: ArrError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrTagsContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.tags.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.tags.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    RadarrTagsContent(
                        rows = s.tags
                            .sortedBy { it.label }
                            .map { tag -> tag.toRow(s.detailsById[tag.id], tag.id in s.queuedRemovedIds) },
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeTags().collect { tags ->
                _state.update { it.copy(loaded = true, loadError = null, tags = tags) }
            }
        }
        viewModelScope.launch {
            repository.observeTagDetails().collect { details ->
                _state.update { it.copy(detailsById = details.associateBy { detail -> detail.id }) }
            }
        }
        viewModelScope.launch {
            repository.queuedRemovedTagIds().collect { ids ->
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
            when (val result = repository.refreshTagsIfStale()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false) }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (it.tags.isEmpty()) result.error else null,
                        writeError = if (it.tags.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun startCreate() {
        _state.update { it.copy(editorOpen = true, editingTagId = null, editingLabel = "") }
    }

    fun startRename(tag: TagResource) {
        _state.update { it.copy(editorOpen = true, editingTagId = tag.id, editingLabel = tag.label.orEmpty()) }
    }

    fun editLabel(label: String) {
        _state.update { it.copy(editingLabel = label) }
    }

    fun editorCancelled() {
        _state.update { it.copy(editorOpen = false, editingTagId = null, editingLabel = "") }
    }

    /** True when the dialog's label field is valid for the current mode. */
    fun editorLabelValid(): Boolean {
        val s = _state.value
        val label = s.editingLabel.trim()
        if (label.isEmpty()) return false
        val clash = s.tags.any { it.label.equals(label, ignoreCase = true) && it.id != s.editingTagId }
        return !clash
    }

    fun editorConfirmed() {
        val s = _state.value
        val label = s.editingLabel.trim()
        if (label.isEmpty() || s.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = if (s.editingTagId == null) {
                repository.saveTag(TagResource(label = label))
            } else {
                repository.saveTag(TagResource(id = s.editingTagId, label = label))
            }
            when (result) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        editorOpen = false,
                        editingTagId = null,
                        editingLabel = "",
                        notice = noticeFor(
                            result.value,
                            created = s.editingTagId == null,
                        ),
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, notice = null, writeError = result.error)
                }
            }
        }
    }

    fun requestDelete(tag: TagResource) {
        _state.update { it.copy(confirmDeleteFor = tag) }
    }

    fun deleteCancelled() {
        _state.update { it.copy(confirmDeleteFor = null) }
    }

    fun deleteConfirmed() {
        val target = _state.value.confirmDeleteFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, confirmDeleteFor = null) }
            when (val result = repository.deleteTag(target.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            TagsNotice.DELETED_QUEUED
                        } else {
                            TagsNotice.DELETED
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

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }

    private fun noticeFor(outcome: WriteOutcome, created: Boolean): TagsNotice =
        if (outcome is WriteOutcome.Queued) {
            if (created) TagsNotice.CREATED_QUEUED else TagsNotice.RENAMED_QUEUED
        } else {
            if (created) TagsNotice.CREATED else TagsNotice.RENAMED
        }

    private fun TagResource.toRow(details: TagDetailsResource?, queuedForRemoval: Boolean) =
        RadarrTagRow(
            tag = this,
            label = label.orEmpty(),
            queuedForRemoval = queuedForRemoval,
            movieCount = details?.movieIds?.size ?: 0,
            importListCount = details?.importListIds?.size ?: 0,
            notificationCount = details?.notificationIds?.size ?: 0,
            releaseProfileCount = details?.releaseProfileIds?.size ?: 0,
            indexerCount = details?.indexerIds?.size ?: 0,
            downloadClientCount = details?.downloadClientIds?.size ?: 0,
            delayProfileCount = details?.delayProfileIds?.size ?: 0,
            autoTagCount = details?.autoTagIds?.size ?: 0,
        )
}
