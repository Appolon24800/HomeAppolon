package dev.appolon.homelab.arr.feature.prowlarr.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.FieldForms
import dev.appolon.homelab.arr.feature.prowlarr.settings.data.GrabTarget
import dev.appolon.homelab.arr.feature.prowlarr.settings.data.GrabTargets
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
 * Editable download client draft. Dynamic provider settings live in [values]
 * keyed by field name; the grab-target category mappings live in [targets]
 * as structured rows.
 */
data class DownloadClientDraft(
    val id: Int = 0,
    val name: String = "",
    val tagLabels: List<String> = emptyList(),
    val fields: List<dev.appolon.homelab.arr.api.prowlarr.model.Field> = emptyList(),
    val values: Map<String, JsonElement> = emptyMap(),
    val implementation: String? = null,
    val implementationName: String? = null,
    val infoLink: String? = null,
    val enable: Boolean = true,
    val priority: Int = 1,
    val supportsCategories: Boolean = false,
    val targets: List<GrabTarget> = emptyList(),
)

/** Transient results of editor actions, mapped to strings by the screen. */
enum class DownloadClientEditorNotice {
    SAVED,
    SAVED_QUEUED,
    DELETED,
    DELETED_QUEUED,
    TEST_PASSED,
}

/**
 * Download client add/edit state. A null clientId starts at the definition
 * picker over the schema list. When the implementation supports categories,
 * the form edits the grab-target mappings: which newznab categories route to
 * which client-side category, with priority deciding between clients.
 */
class ProwlarrDownloadClientEditorViewModel(
    private val serverId: ServerId,
    private val clientId: Int?,
    private val repository: ProwlarrSettingsRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val loadError: ArrError? = null,
        val isNew: Boolean = true,
        val schemas: List<DownloadClientResource> = emptyList(),
        val tags: List<TagResource> = emptyList(),
        /** Root newznab categories offered by the grab-target editor. */
        val categoryOptions: List<IndexerCategory> = emptyList(),
        val draft: DownloadClientDraft? = null,
        val testing: Boolean = false,
        val confirmDelete: Boolean = false,
        val writeError: ArrError? = null,
        val notice: DownloadClientEditorNotice? = null,
        val busy: Boolean = false,
    ) {
        val picker: Boolean get() = isNew && draft == null
    }

    private val _state = MutableStateFlow(UiState(isNew = clientId == null))
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
            loadCategoryOptions()
            when (clientId) {
                null -> when (val schemas = repository.downloadClientSchemas()) {
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

    /**
     * Loads the root newznab categories for the grab-target chips. The table
     * is shared with the indexers group; failures fall back to the cache so
     * the editor keeps working offline.
     */
    private suspend fun loadCategoryOptions() {
        when (val categories = repository.newznabCategories()) {
            is ArrResult.Ok -> _state.update {
                it.copy(categoryOptions = categories.value.sortedBy { category -> category.id })
            }

            is ArrResult.Err -> Unit // chips fall back to raw ids
        }
    }

    private fun draftOf(resource: DownloadClientResource): DownloadClientDraft {
        val byId = _state.value.tags.associate { it.id to it.label.orEmpty() }
        return DownloadClientDraft(
            id = resource.id,
            name = resource.name.orEmpty(),
            tagLabels = resource.tags.orEmpty().mapNotNull { byId[it] },
            fields = resource.fields.orEmpty(),
            values = FieldForms.editableValues(resource.fields),
            implementation = resource.implementation,
            implementationName = resource.implementationName,
            infoLink = resource.infoLink,
            enable = resource.enable,
            priority = resource.priority,
            supportsCategories = resource.supportsCategories,
            targets = GrabTargets.of(resource),
        )
    }

    private fun loadExisting() {
        viewModelScope.launch {
            var found = repository.cachedDownloadClients().firstOrNull { it.id == clientId }
            if (found == null) {
                when (val refreshed = repository.refresh()) {
                    is ArrResult.Ok -> found = repository.cachedDownloadClients()
                        .firstOrNull { it.id == clientId }

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
    fun pickSchema(schema: DownloadClientResource) {
        _state.update { state ->
            state.copy(draft = draftOf(schema.copy(name = schema.name ?: schema.implementationName)))
        }
    }

    fun updateDraft(transform: (DownloadClientDraft) -> DownloadClientDraft) {
        _state.update { s -> s.copy(draft = s.draft?.let(transform)) }
    }

    fun onValueChange(name: String, value: JsonElement) {
        updateDraft { it.copy(values = it.values + (name to value)) }
    }

    fun setPriority(priority: Int) {
        updateDraft { it.copy(priority = priority.coerceAtLeast(1)) }
    }

    fun toggleTag(label: String) {
        updateDraft {
            it.copy(
                tagLabels = if (label in it.tagLabels) it.tagLabels - label else it.tagLabels + label,
            )
        }
    }

    /** Adds one empty mapping row for the grab-target editor. */
    fun addGrabTarget() {
        updateDraft { it.copy(targets = it.targets + GrabTarget()) }
    }

    fun removeGrabTarget(index: Int) {
        updateDraft {
            it.copy(targets = it.targets.filterIndexed { i, _ -> i != index })
        }
    }

    fun setGrabTargetCategory(index: Int, clientCategory: String) {
        updateDraft {
            it.copy(
                targets = it.targets.mapIndexed { i, target ->
                    if (i == index) target.copy(clientCategory = clientCategory) else target
                },
            )
        }
    }

    fun toggleGrabTargetNewznabCategory(index: Int, categoryId: Int) {
        updateDraft {
            it.copy(
                targets = it.targets.mapIndexed { i, target ->
                    if (i == index) {
                        val current = target.categories
                        target.copy(
                            categories = if (categoryId in current) {
                                current - categoryId
                            } else {
                                current + categoryId
                            },
                        )
                    } else {
                        target
                    }
                },
            )
        }
    }

    private suspend fun buildResource(draft: DownloadClientDraft): ArrResult<DownloadClientResource> {
        val tagIds = when (val ids = repository.ensureTagIds(draft.tagLabels)) {
            is ArrResult.Ok -> ids.value
            is ArrResult.Err -> return ArrResult.err(ids.error)
        }
        var resource = DownloadClientResource(
            id = draft.id,
            name = draft.name.trim(),
            fields = FieldForms.applyValues(draft.fields, draft.values),
            implementation = draft.implementation,
            implementationName = draft.implementationName,
            infoLink = draft.infoLink,
            tags = tagIds.toSet().toList(),
            enable = draft.enable,
            priority = draft.priority,
            supportsCategories = draft.supportsCategories,
        )
        if (draft.supportsCategories) {
            resource = GrabTargets.applyOnto(resource, draft.targets)
        }
        return ArrResult.ok(resource)
    }

    fun save(onSaved: () -> Unit = {}) {
        val draft = _state.value.draft ?: return
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val built = buildResource(draft)) {
                is ArrResult.Err -> _state.update { it.copy(busy = false, writeError = built.error) }

                is ArrResult.Ok -> when (val saved = repository.saveDownloadClient(built.value)) {
                    is ArrResult.Ok -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                writeError = null,
                                isNew = false,
                                notice = if (saved.value is WriteOutcome.Queued) {
                                    DownloadClientEditorNotice.SAVED_QUEUED
                                } else {
                                    DownloadClientEditorNotice.SAVED
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

    /** Sends the current draft as a probe; never queues. */
    fun test() {
        val draft = _state.value.draft ?: return
        viewModelScope.launch {
            _state.update { it.copy(testing = true) }
            when (val built = buildResource(draft)) {
                is ArrResult.Err -> _state.update { it.copy(testing = false, writeError = built.error) }

                is ArrResult.Ok -> when (val tested = repository.testDownloadClient(built.value)) {
                    is ArrResult.Ok -> _state.update {
                        it.copy(testing = false, notice = DownloadClientEditorNotice.TEST_PASSED)
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
            when (val removed = repository.removeDownloadClient(id)) {
                is ArrResult.Ok -> {
                    _state.update {
                        it.copy(
                            busy = false,
                            notice = if (removed.value is WriteOutcome.Queued) {
                                DownloadClientEditorNotice.DELETED_QUEUED
                            } else {
                                DownloadClientEditorNotice.DELETED
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
