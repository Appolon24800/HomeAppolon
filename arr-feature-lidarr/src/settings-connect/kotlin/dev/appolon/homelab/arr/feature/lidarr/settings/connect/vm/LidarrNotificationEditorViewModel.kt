package dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.Field
import dev.appolon.homelab.arr.api.lidarr.model.NotificationResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.data.LidarrConnectFieldForms
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
import kotlinx.serialization.json.JsonElement

/**
 * One event-matrix toggle: the wire flag, whether the implementation
 * supports it, and the label resource handled by the screen.
 */
data class NotificationEventToggle(
    val key: String,
    val supported: Boolean,
    val enabled: Boolean,
)

/** Editable notification draft; [events] is the full Lidarr event matrix. */
data class NotificationDraft(
    val id: Int = 0,
    val name: String = "",
    val tagLabels: List<String> = emptyList(),
    val fields: List<Field> = emptyList(),
    val values: Map<String, JsonElement> = emptyMap(),
    val implementation: String? = null,
    val implementationName: String? = null,
    val infoLink: String? = null,
    val onGrab: Boolean = false,
    val onReleaseImport: Boolean = false,
    val onUpgrade: Boolean = false,
    val onRename: Boolean = false,
    val onArtistAdd: Boolean = false,
    val onArtistDelete: Boolean = false,
    val onAlbumDelete: Boolean = false,
    val onHealthIssue: Boolean = false,
    val onHealthRestored: Boolean = false,
    val onDownloadFailure: Boolean = false,
    val onImportFailure: Boolean = false,
    val onTrackRetag: Boolean = false,
    val onApplicationUpdate: Boolean = false,
    val includeHealthWarnings: Boolean = false,
    val supportsOnGrab: Boolean = false,
    val supportsOnReleaseImport: Boolean = false,
    val supportsOnUpgrade: Boolean = false,
    val supportsOnRename: Boolean = false,
    val supportsOnArtistAdd: Boolean = false,
    val supportsOnArtistDelete: Boolean = false,
    val supportsOnAlbumDelete: Boolean = false,
    val supportsOnHealthIssue: Boolean = false,
    val supportsOnHealthRestored: Boolean = false,
    val supportsOnDownloadFailure: Boolean = false,
    val supportsOnImportFailure: Boolean = false,
    val supportsOnTrackRetag: Boolean = false,
    val supportsOnApplicationUpdate: Boolean = false,
) {
    /** The event matrix rows in Lidarr UI order, unsupported events excluded. */
    val events: List<NotificationEventToggle> = listOf(
        NotificationEventToggle("onGrab", supportsOnGrab, onGrab),
        NotificationEventToggle("onReleaseImport", supportsOnReleaseImport, onReleaseImport),
        NotificationEventToggle("onUpgrade", supportsOnUpgrade, onUpgrade),
        NotificationEventToggle("onRename", supportsOnRename, onRename),
        NotificationEventToggle("onArtistAdd", supportsOnArtistAdd, onArtistAdd),
        NotificationEventToggle("onArtistDelete", supportsOnArtistDelete, onArtistDelete),
        NotificationEventToggle("onAlbumDelete", supportsOnAlbumDelete, onAlbumDelete),
        NotificationEventToggle("onHealthIssue", supportsOnHealthIssue, onHealthIssue),
        NotificationEventToggle("onHealthRestored", supportsOnHealthRestored, onHealthRestored),
        NotificationEventToggle("onDownloadFailure", supportsOnDownloadFailure, onDownloadFailure),
        NotificationEventToggle("onImportFailure", supportsOnImportFailure, onImportFailure),
        NotificationEventToggle("onTrackRetag", supportsOnTrackRetag, onTrackRetag),
        NotificationEventToggle("onApplicationUpdate", supportsOnApplicationUpdate, onApplicationUpdate),
    ).filter { it.supported }
}

/** Transient results of notification editor actions, mapped to strings by the screen. */
enum class NotificationEditorNotice {
    SAVED,
    SAVED_QUEUED,
    DELETED,
    DELETED_QUEUED,
    TEST_PASSED,
}

/**
 * Notification editor state. A null notificationId starts at the
 * definition picker over the schema list. The event matrix renders only
 * the events the chosen implementation supports; test sends the current
 * draft, saved or not.
 */
class LidarrNotificationEditorViewModel(
    private val serverId: ServerId,
    private val notificationId: Int?,
    private val repository: LidarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val loadError: ArrError? = null,
        val isNew: Boolean = true,
        val schemas: List<NotificationResource> = emptyList(),
        val tags: List<TagResource> = emptyList(),
        val draft: NotificationDraft? = null,
        val testing: Boolean = false,
        val confirmDelete: Boolean = false,
        val writeError: ArrError? = null,
        val testError: ArrError? = null,
        val notice: NotificationEditorNotice? = null,
        val busy: Boolean = false,
    ) {
        val picker: Boolean get() = isNew && draft == null
    }

    private val _state = MutableStateFlow(UiState(isNew = notificationId == null))
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<UiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.draft == null && !s.picker -> LceState.Error(s.loadError)
                s.loading && s.picker && s.schemas.isEmpty() -> LceState.Loading
                s.loading && !s.picker -> LceState.Loading
                else -> LceState.Content(s)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeTags().collect { tags -> _state.update { it.copy(tags = tags) } }
        }
        viewModelScope.launch {
            repository.tags()
            when (notificationId) {
                null -> when (val schemas = repository.notificationSchemas()) {
                    is ArrResult.Ok -> _state.update {
                        it.copy(
                            loading = false,
                            loadError = null,
                            schemas = schemas.value.sortedBy { schema -> schema.implementationName },
                        )
                    }

                    is ArrResult.Err -> _state.update {
                        it.copy(loading = false, loadError = schemas.error)
                    }
                }

                else -> loadExisting()
            }
        }
    }

    private fun draftOf(resource: NotificationResource): NotificationDraft {
        val byId = _state.value.tags.associate { it.id to it.label.orEmpty() }
        return NotificationDraft(
            id = resource.id,
            name = resource.name.orEmpty(),
            tagLabels = resource.tags.orEmpty().mapNotNull { byId[it] },
            fields = resource.fields.orEmpty(),
            values = LidarrConnectFieldForms.editableValues(resource.fields),
            implementation = resource.implementation,
            implementationName = resource.implementationName,
            infoLink = resource.infoLink,
            onGrab = resource.onGrab,
            onReleaseImport = resource.onReleaseImport,
            onUpgrade = resource.onUpgrade,
            onRename = resource.onRename,
            onArtistAdd = resource.onArtistAdd,
            onArtistDelete = resource.onArtistDelete,
            onAlbumDelete = resource.onAlbumDelete,
            onHealthIssue = resource.onHealthIssue,
            onHealthRestored = resource.onHealthRestored,
            onDownloadFailure = resource.onDownloadFailure,
            onImportFailure = resource.onImportFailure,
            onTrackRetag = resource.onTrackRetag,
            onApplicationUpdate = resource.onApplicationUpdate,
            includeHealthWarnings = resource.includeHealthWarnings,
            supportsOnGrab = resource.supportsOnGrab,
            supportsOnReleaseImport = resource.supportsOnReleaseImport,
            supportsOnUpgrade = resource.supportsOnUpgrade,
            supportsOnRename = resource.supportsOnRename,
            supportsOnArtistAdd = resource.supportsOnArtistAdd,
            supportsOnArtistDelete = resource.supportsOnArtistDelete,
            supportsOnAlbumDelete = resource.supportsOnAlbumDelete,
            supportsOnHealthIssue = resource.supportsOnHealthIssue,
            supportsOnHealthRestored = resource.supportsOnHealthRestored,
            supportsOnDownloadFailure = resource.supportsOnDownloadFailure,
            supportsOnImportFailure = resource.supportsOnImportFailure,
            supportsOnTrackRetag = resource.supportsOnTrackRetag,
            supportsOnApplicationUpdate = resource.supportsOnApplicationUpdate,
        )
    }

    private fun loadExisting() {
        viewModelScope.launch {
            var found = repository.cachedNotifications().firstOrNull { it.id == notificationId }
            if (found == null) {
                when (val refreshed = repository.refresh()) {
                    is ArrResult.Ok -> found = repository.cachedNotifications()
                        .firstOrNull { it.id == notificationId }

                    is ArrResult.Err -> {
                        _state.update { it.copy(loading = false, loadError = refreshed.error) }
                        return@launch
                    }
                }
            }
            _state.update {
                it.copy(
                    loading = false,
                    loadError = if (found == null) ArrError.Http(404, null) else null,
                    draft = found?.let { row -> draftOf(row) },
                )
            }
        }
    }

    /** Builds the draft from a picked definition, defaulting the name. */
    fun pickSchema(schema: NotificationResource) {
        _state.update { state ->
            state.copy(draft = draftOf(schema.copy(name = schema.name ?: schema.implementationName)))
        }
    }

    fun updateDraft(transform: (NotificationDraft) -> NotificationDraft) {
        _state.update { s -> s.copy(draft = s.draft?.let(transform)) }
    }

    fun onValueChange(name: String, value: JsonElement) {
        updateDraft { it.copy(values = it.values + (name to value)) }
    }

    fun toggleTag(label: String) {
        updateDraft {
            it.copy(
                tagLabels = if (label in it.tagLabels) it.tagLabels - label else it.tagLabels + label,
            )
        }
    }

    /** Flips one event-matrix flag by its wire key. */
    fun toggleEvent(key: String) {
        updateDraft { draft ->
            when (key) {
                "onGrab" -> draft.copy(onGrab = !draft.onGrab)
                "onReleaseImport" -> draft.copy(onReleaseImport = !draft.onReleaseImport)
                "onUpgrade" -> draft.copy(onUpgrade = !draft.onUpgrade)
                "onRename" -> draft.copy(onRename = !draft.onRename)
                "onArtistAdd" -> draft.copy(onArtistAdd = !draft.onArtistAdd)
                "onArtistDelete" -> draft.copy(onArtistDelete = !draft.onArtistDelete)
                "onAlbumDelete" -> draft.copy(onAlbumDelete = !draft.onAlbumDelete)
                "onHealthIssue" -> draft.copy(onHealthIssue = !draft.onHealthIssue)
                "includeHealthWarnings" ->
                    draft.copy(includeHealthWarnings = !draft.includeHealthWarnings)

                "onHealthRestored" -> draft.copy(onHealthRestored = !draft.onHealthRestored)
                "onDownloadFailure" -> draft.copy(onDownloadFailure = !draft.onDownloadFailure)
                "onImportFailure" -> draft.copy(onImportFailure = !draft.onImportFailure)
                "onTrackRetag" -> draft.copy(onTrackRetag = !draft.onTrackRetag)
                "onApplicationUpdate" ->
                    draft.copy(onApplicationUpdate = !draft.onApplicationUpdate)

                else -> draft
            }
        }
    }

    private suspend fun buildResource(draft: NotificationDraft): ArrResult<NotificationResource> {
        val tagIds = when (val ids = repository.ensureTagIds(draft.tagLabels)) {
            is ArrResult.Ok -> ids.value
            is ArrResult.Err -> return ArrResult.err(ids.error)
        }
        return ArrResult.ok(
            NotificationResource(
                id = draft.id,
                name = draft.name.trim(),
                fields = LidarrConnectFieldForms.applyValues(draft.fields, draft.values),
                implementation = draft.implementation,
                implementationName = draft.implementationName,
                infoLink = draft.infoLink,
                tags = tagIds.toSet(),
                onGrab = draft.onGrab,
                onReleaseImport = draft.onReleaseImport,
                onUpgrade = draft.onUpgrade,
                onRename = draft.onRename,
                onArtistAdd = draft.onArtistAdd,
                onArtistDelete = draft.onArtistDelete,
                onAlbumDelete = draft.onAlbumDelete,
                onHealthIssue = draft.onHealthIssue,
                onHealthRestored = draft.onHealthRestored,
                onDownloadFailure = draft.onDownloadFailure,
                onImportFailure = draft.onImportFailure,
                onTrackRetag = draft.onTrackRetag,
                onApplicationUpdate = draft.onApplicationUpdate,
                includeHealthWarnings = draft.includeHealthWarnings,
                supportsOnGrab = draft.supportsOnGrab,
                supportsOnReleaseImport = draft.supportsOnReleaseImport,
                supportsOnUpgrade = draft.supportsOnUpgrade,
                supportsOnRename = draft.supportsOnRename,
                supportsOnArtistAdd = draft.supportsOnArtistAdd,
                supportsOnArtistDelete = draft.supportsOnArtistDelete,
                supportsOnAlbumDelete = draft.supportsOnAlbumDelete,
                supportsOnHealthIssue = draft.supportsOnHealthIssue,
                supportsOnHealthRestored = draft.supportsOnHealthRestored,
                supportsOnDownloadFailure = draft.supportsOnDownloadFailure,
                supportsOnImportFailure = draft.supportsOnImportFailure,
                supportsOnTrackRetag = draft.supportsOnTrackRetag,
                supportsOnApplicationUpdate = draft.supportsOnApplicationUpdate,
            ),
        )
    }

    fun save(onSaved: () -> Unit = {}) {
        val draft = _state.value.draft ?: return
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val built = buildResource(draft)) {
                is ArrResult.Err -> _state.update { it.copy(busy = false, writeError = built.error) }

                is ArrResult.Ok -> when (val saved = repository.saveNotification(built.value)) {
                    is ArrResult.Ok -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                writeError = null,
                                isNew = false,
                                notice = if (saved.value is WriteOutcome.Queued) {
                                    NotificationEditorNotice.SAVED_QUEUED
                                } else {
                                    NotificationEditorNotice.SAVED
                                },
                            )
                        }
                        onSaved()
                    }

                    is ArrResult.Err -> _state.update {
                        it.copy(busy = false, writeError = saved.error)
                    }
                }
            }
        }
    }

    fun test() {
        val draft = _state.value.draft ?: return
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testError = null) }
            when (val built = buildResource(draft)) {
                is ArrResult.Err -> _state.update { it.copy(testing = false, testError = built.error) }

                is ArrResult.Ok -> when (val tested = repository.testNotification(built.value)) {
                    is ArrResult.Ok -> _state.update {
                        it.copy(testing = false, notice = NotificationEditorNotice.TEST_PASSED)
                    }

                    is ArrResult.Err -> _state.update {
                        it.copy(testing = false, testError = tested.error)
                    }
                }
            }
        }
    }

    fun requestDelete() {
        _state.update { it.copy(confirmDelete = true) }
    }

    fun deleteCancelled() {
        _state.update { it.copy(confirmDelete = false) }
    }

    fun deleteConfirmed(onDone: () -> Unit) {
        val id = _state.value.draft?.id ?: return
        if (id <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, confirmDelete = false) }
            when (val removed = repository.removeNotification(id)) {
                is ArrResult.Ok -> {
                    _state.update {
                        it.copy(
                            busy = false,
                            notice = if (removed.value is WriteOutcome.Queued) {
                                NotificationEditorNotice.DELETED_QUEUED
                            } else {
                                NotificationEditorNotice.DELETED
                            },
                        )
                    }
                    onDone()
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

    fun consumeTestError() {
        _state.update { it.copy(testError = null) }
    }
}
