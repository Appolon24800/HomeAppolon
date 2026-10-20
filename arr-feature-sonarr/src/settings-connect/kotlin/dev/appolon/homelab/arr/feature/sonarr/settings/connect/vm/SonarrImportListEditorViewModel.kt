package dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.Field
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.NewItemMonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.SeriesTypes
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.data.SonarrConnectFieldForms
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
import kotlinx.serialization.json.JsonElement

/**
 * Editable import list draft. Beyond the dynamic provider fields, a
 * Sonarr list carries the add options every synced series inherits:
 * monitoring strategy, new-item monitoring, series type, season folder,
 * quality profile, and root folder.
 */
data class ImportListDraft(
    val id: Int = 0,
    val name: String = "",
    val enableAutomaticAdd: Boolean = true,
    val searchForMissingEpisodes: Boolean = false,
    val seasonFolder: Boolean = true,
    val shouldMonitor: MonitorTypes = MonitorTypes.ALL,
    val monitorNewItems: NewItemMonitorTypes = NewItemMonitorTypes.ALL,
    val seriesType: SeriesTypes = SeriesTypes.STANDARD,
    val rootFolderPath: String = "",
    val qualityProfileId: Int = 0,
    val tagLabels: List<String> = emptyList(),
    val fields: List<Field> = emptyList(),
    val values: Map<String, JsonElement> = emptyMap(),
    val implementation: String? = null,
    val implementationName: String? = null,
    val infoLink: String? = null,
    val listType: String? = null,
)

/** Transient results of import list editor actions, mapped to strings by the screen. */
enum class ImportListEditorNotice {
    SAVED,
    SAVED_QUEUED,
    DELETED,
    DELETED_QUEUED,
    TEST_PASSED,
}

/**
 * Import list editor state. A null listId starts at the definition picker
 * over the schema list; after a pick (or when editing) the form shows the
 * add options plus the per-connection dynamic fields. Test sends a probe
 * with the draft values.
 */
class SonarrImportListEditorViewModel(
    private val serverId: ServerId,
    private val listId: Int?,
    private val repository: SonarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val loadError: ArrError? = null,
        val isNew: Boolean = true,
        val schemas: List<ImportListResource> = emptyList(),
        val tags: List<TagResource> = emptyList(),
        val draft: ImportListDraft? = null,
        val testing: Boolean = false,
        val confirmDelete: Boolean = false,
        val writeError: ArrError? = null,
        val testError: ArrError? = null,
        val notice: ImportListEditorNotice? = null,
        val busy: Boolean = false,
    ) {
        val picker: Boolean get() = isNew && draft == null
    }

    private val _state = MutableStateFlow(UiState(isNew = listId == null))
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
            when (listId) {
                null -> when (val schemas = repository.importListSchemas()) {
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

    private fun draftOf(resource: ImportListResource): ImportListDraft {
        val byId = _state.value.tags.associate { it.id to it.label.orEmpty() }
        return ImportListDraft(
            id = resource.id,
            name = resource.name.orEmpty(),
            enableAutomaticAdd = resource.enableAutomaticAdd,
            searchForMissingEpisodes = resource.searchForMissingEpisodes,
            seasonFolder = resource.seasonFolder,
            shouldMonitor = resource.shouldMonitor,
            monitorNewItems = resource.monitorNewItems,
            seriesType = resource.seriesType,
            rootFolderPath = resource.rootFolderPath.orEmpty(),
            qualityProfileId = resource.qualityProfileId,
            tagLabels = resource.tags.orEmpty().mapNotNull { byId[it] },
            fields = resource.fields.orEmpty(),
            values = SonarrConnectFieldForms.editableValues(resource.fields),
            implementation = resource.implementation,
            implementationName = resource.implementationName,
            infoLink = resource.infoLink,
            listType = resource.listType.name.lowercase(),
        )
    }

    private fun loadExisting() {
        viewModelScope.launch {
            var found = repository.cachedImportLists().firstOrNull { it.id == listId }
            if (found == null) {
                when (val refreshed = repository.refresh()) {
                    is ArrResult.Ok -> found = repository.cachedImportLists()
                        .firstOrNull { it.id == listId }

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
    fun pickSchema(schema: ImportListResource) {
        _state.update { state ->
            state.copy(draft = draftOf(schema.copy(name = schema.name ?: schema.implementationName)))
        }
    }

    fun updateDraft(transform: (ImportListDraft) -> ImportListDraft) {
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

    /** Builds the wire resource from the draft without writing anything. */
    private suspend fun buildResource(draft: ImportListDraft): ArrResult<ImportListResource> {
        val tagIds = when (val ids = repository.ensureTagIds(draft.tagLabels)) {
            is ArrResult.Ok -> ids.value
            is ArrResult.Err -> return ArrResult.err(ids.error)
        }
        return ArrResult.ok(
            ImportListResource(
                id = draft.id,
                name = draft.name.trim(),
                fields = SonarrConnectFieldForms.applyValues(draft.fields, draft.values),
                implementation = draft.implementation,
                implementationName = draft.implementationName,
                infoLink = draft.infoLink,
                tags = tagIds,
                enableAutomaticAdd = draft.enableAutomaticAdd,
                searchForMissingEpisodes = draft.searchForMissingEpisodes,
                shouldMonitor = draft.shouldMonitor,
                monitorNewItems = draft.monitorNewItems,
                seriesType = draft.seriesType,
                rootFolderPath = draft.rootFolderPath.trim().ifEmpty { null },
                qualityProfileId = draft.qualityProfileId,
                seasonFolder = draft.seasonFolder,
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

                is ArrResult.Ok -> when (val saved = repository.saveImportList(built.value)) {
                    is ArrResult.Ok -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                writeError = null,
                                isNew = false,
                                notice = if (saved.value is WriteOutcome.Queued) {
                                    ImportListEditorNotice.SAVED_QUEUED
                                } else {
                                    ImportListEditorNotice.SAVED
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

    /** Tests the current draft values; nothing is written to the server. */
    fun test() {
        val draft = _state.value.draft ?: return
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testError = null) }
            when (val built = buildResource(draft)) {
                is ArrResult.Err -> _state.update { it.copy(testing = false, testError = built.error) }

                is ArrResult.Ok -> when (val tested = repository.testImportList(built.value)) {
                    is ArrResult.Ok -> _state.update {
                        it.copy(testing = false, notice = ImportListEditorNotice.TEST_PASSED)
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
            when (val removed = repository.removeImportList(id)) {
                is ArrResult.Ok -> {
                    _state.update {
                        it.copy(
                            busy = false,
                            notice = if (removed.value is WriteOutcome.Queued) {
                                ImportListEditorNotice.DELETED_QUEUED
                            } else {
                                ImportListEditorNotice.DELETED
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
