package dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.sonarr.model.Field
import dev.appolon.homelab.arr.api.sonarr.model.IndexerResource
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
 * Editable indexer draft. [fields] stays the schema's field descriptor
 * list; Sonarr's categories and seed criteria render out of it. The
 * season search age bound is a Sonarr-specific column on the resource.
 */
data class IndexerDraft(
    val id: Int = 0,
    val name: String = "",
    val enableRss: Boolean = true,
    val enableAutomaticSearch: Boolean = true,
    val enableInteractiveSearch: Boolean = true,
    val priority: Int = 25,
    val seasonSearchMaximumSingleEpisodeAge: Int = 0,
    val tagLabels: List<String> = emptyList(),
    val fields: List<Field> = emptyList(),
    val values: Map<String, JsonElement> = emptyMap(),
    val implementation: String? = null,
    val implementationName: String? = null,
    val infoLink: String? = null,
    val supportsRss: Boolean = true,
    val supportsSearch: Boolean = true,
    val protocol: DownloadProtocol = DownloadProtocol.UNKNOWN,
)

/** Transient results of indexer editor actions, mapped to strings by the screen. */
enum class IndexerEditorNotice {
    SAVED,
    SAVED_QUEUED,
    DELETED,
    DELETED_QUEUED,
    TEST_PASSED,
}

/**
 * Indexer editor state. A null indexerId starts at the definition picker
 * over the schema list; picking one builds the draft from its field
 * descriptors (categories and seed settings render from the same list).
 * Saving resolves tag labels to ids first, then writes through with the
 * offline queue fallback. Test sends the current draft, saved or not.
 */
class SonarrIndexerEditorViewModel(
    private val serverId: ServerId,
    private val indexerId: Int?,
    private val repository: SonarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val loadError: ArrError? = null,
        val isNew: Boolean = true,
        val schemas: List<IndexerResource> = emptyList(),
        val tags: List<TagResource> = emptyList(),
        val draft: IndexerDraft? = null,
        val testing: Boolean = false,
        val confirmDelete: Boolean = false,
        val writeError: ArrError? = null,
        val testError: ArrError? = null,
        val notice: IndexerEditorNotice? = null,
        val busy: Boolean = false,
    ) {
        val picker: Boolean get() = isNew && draft == null
    }

    private val _state = MutableStateFlow(UiState(isNew = indexerId == null))
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
            when (indexerId) {
                null -> when (val schemas = repository.indexerSchemas()) {
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

    private suspend fun draftOf(resource: IndexerResource): IndexerDraft {
        val byId = _state.value.tags.associate { it.id to it.label.orEmpty() }
        return IndexerDraft(
            id = resource.id,
            name = resource.name.orEmpty(),
            enableRss = resource.enableRss,
            enableAutomaticSearch = resource.enableAutomaticSearch,
            enableInteractiveSearch = resource.enableInteractiveSearch,
            priority = resource.priority,
            seasonSearchMaximumSingleEpisodeAge = resource.seasonSearchMaximumSingleEpisodeAge,
            tagLabels = resource.tags.orEmpty().mapNotNull { byId[it] },
            fields = resource.fields.orEmpty(),
            values = SonarrConnectFieldForms.editableValues(resource.fields),
            implementation = resource.implementation,
            implementationName = resource.implementationName,
            infoLink = resource.infoLink,
            supportsRss = resource.supportsRss,
            supportsSearch = resource.supportsSearch,
            protocol = resource.protocol,
        )
    }

    private fun loadExisting() {
        viewModelScope.launch {
            var found = repository.cachedIndexers().firstOrNull { it.id == indexerId }
            if (found == null) {
                when (val refreshed = repository.refresh()) {
                    is ArrResult.Ok -> found = repository.cachedIndexers()
                        .firstOrNull { it.id == indexerId }

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
    fun pickSchema(schema: IndexerResource) {
        _state.update { state ->
            val name = schema.name ?: schema.implementationName
            state.copy(draft = draftFromSchema(schema.copy(name = name)))
        }
    }

    private fun draftFromSchema(schema: IndexerResource): IndexerDraft =
        IndexerDraft(
            name = schema.name.orEmpty(),
            enableRss = schema.supportsRss,
            enableAutomaticSearch = schema.supportsSearch,
            enableInteractiveSearch = schema.supportsSearch,
            priority = schema.priority,
            seasonSearchMaximumSingleEpisodeAge = schema.seasonSearchMaximumSingleEpisodeAge,
            fields = schema.fields.orEmpty(),
            values = SonarrConnectFieldForms.editableValues(schema.fields),
            implementation = schema.implementation,
            implementationName = schema.implementationName,
            infoLink = schema.infoLink,
            supportsRss = schema.supportsRss,
            supportsSearch = schema.supportsSearch,
            protocol = schema.protocol,
        )

    fun updateDraft(transform: (IndexerDraft) -> IndexerDraft) {
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
    private suspend fun buildResource(draft: IndexerDraft): ArrResult<IndexerResource> {
        val tagIds = when (val ids = repository.ensureTagIds(draft.tagLabels)) {
            is ArrResult.Ok -> ids.value
            is ArrResult.Err -> return ArrResult.err(ids.error)
        }
        return ArrResult.ok(
            IndexerResource(
                id = draft.id,
                name = draft.name.trim(),
                fields = SonarrConnectFieldForms.applyValues(draft.fields, draft.values),
                implementation = draft.implementation,
                implementationName = draft.implementationName,
                infoLink = draft.infoLink,
                tags = tagIds.toSet(),
                enableRss = draft.enableRss,
                enableAutomaticSearch = draft.enableAutomaticSearch,
                enableInteractiveSearch = draft.enableInteractiveSearch,
                supportsRss = draft.supportsRss,
                supportsSearch = draft.supportsSearch,
                protocol = draft.protocol,
                priority = draft.priority,
                seasonSearchMaximumSingleEpisodeAge = draft.seasonSearchMaximumSingleEpisodeAge,
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

                is ArrResult.Ok -> when (val saved = repository.saveIndexer(built.value)) {
                    is ArrResult.Ok -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                writeError = null,
                                isNew = false,
                                notice = if (saved.value is WriteOutcome.Queued) {
                                    IndexerEditorNotice.SAVED_QUEUED
                                } else {
                                    IndexerEditorNotice.SAVED
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

                is ArrResult.Ok -> when (val tested = repository.testIndexer(built.value)) {
                    is ArrResult.Ok -> _state.update {
                        it.copy(testing = false, notice = IndexerEditorNotice.TEST_PASSED)
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
            when (val removed = repository.removeIndexer(id)) {
                is ArrResult.Ok -> {
                    _state.update {
                        it.copy(
                            busy = false,
                            notice = if (removed.value is WriteOutcome.Queued) {
                                IndexerEditorNotice.DELETED_QUEUED
                            } else {
                                IndexerEditorNotice.DELETED
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
