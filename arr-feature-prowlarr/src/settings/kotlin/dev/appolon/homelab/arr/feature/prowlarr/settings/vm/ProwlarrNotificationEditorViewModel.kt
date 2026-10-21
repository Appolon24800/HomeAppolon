package dev.appolon.homelab.arr.feature.prowlarr.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.Field
import dev.appolon.homelab.arr.api.prowlarr.model.NotificationResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.FieldForms
import dev.appolon.homelab.arr.feature.prowlarr.settings.data.NotificationEvents
import dev.appolon.homelab.arr.feature.prowlarr.settings.data.ProwlarrSettingsRepository
import dev.appolon.homelab.arr.feature.prowlarr.settings.data.WriteOutcome
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
 * Editable notification draft. [events] is the event matrix restricted to
 * what the implementation supports; the per-connection dynamic settings live
 * in [values] keyed by field name.
 */
data class NotificationDraft(
    val id: Int = 0,
    val name: String = "",
    val tagLabels: List<String> = emptyList(),
    val fields: List<Field> = emptyList(),
    val values: Map<String, JsonElement> = emptyMap(),
    val implementation: String? = null,
    val implementationName: String? = null,
    val infoLink: String? = null,
    val resource: NotificationResource = NotificationResource(),
) {
    /** The full event matrix rows; unsupported events never render. */
    val events get() = NotificationEvents.of(resource)
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
 * Notification add/edit state. A null notificationId starts at the
 * definition picker over the schema list. The event matrix renders only the
 * events the chosen implementation supports, with manual-grab and
 * health-warning sub-toggles under their parents; test sends the current
 * draft, saved or not.
 */
class ProwlarrNotificationEditorViewModel(
    private val serverId: ServerId,
    private val notificationId: Int?,
    private val repository: ProwlarrSettingsRepository,
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
            values = FieldForms.editableValues(resource.fields),
            implementation = resource.implementation,
            implementationName = resource.implementationName,
            infoLink = resource.infoLink,
            resource = resource,
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
            state.copy(
                draft = draftOf(schema.copy(name = schema.name ?: schema.implementationName)),
            )
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
            draft.copy(resource = NotificationEvents.toggle(draft.resource, key))
        }
    }

    private suspend fun buildResource(draft: NotificationDraft): ArrResult<NotificationResource> {
        val tagIds = when (val ids = repository.ensureTagIds(draft.tagLabels)) {
            is ArrResult.Ok -> ids.value
            is ArrResult.Err -> return ArrResult.err(ids.error)
        }
        return ArrResult.ok(
            draft.resource.copy(
                id = draft.id,
                name = draft.name.trim(),
                fields = FieldForms.applyValues(draft.fields, draft.values),
                implementation = draft.implementation,
                implementationName = draft.implementationName,
                infoLink = draft.infoLink,
                tags = tagIds.toSet().toList(),
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
            _state.update { it.copy(testing = true) }
            when (val built = buildResource(draft)) {
                is ArrResult.Err -> _state.update { it.copy(testing = false, writeError = built.error) }

                is ArrResult.Ok -> when (val tested = repository.testNotification(built.value)) {
                    is ArrResult.Ok -> _state.update {
                        it.copy(testing = false, notice = NotificationEditorNotice.TEST_PASSED)
                    }

                    is ArrResult.Err -> _state.update {
                        it.copy(testing = false, writeError = tested.error)
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
}
