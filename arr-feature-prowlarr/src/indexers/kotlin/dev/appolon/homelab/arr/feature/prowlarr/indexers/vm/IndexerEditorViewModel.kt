package dev.appolon.homelab.arr.feature.prowlarr.indexers.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.Field
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.FieldForms
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.IndexerCategories
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProtocolFilter
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.WriteOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** Transient one-line result of the editor, surfaced as a snackbar. */
sealed interface IndexerEditorMessage {
    data object Saved : IndexerEditorMessage
    data object SavedQueued : IndexerEditorMessage
    data object TestPassed : IndexerEditorMessage
    data class TestFailed(val error: ArrError) : IndexerEditorMessage
    data class WriteFailed(val error: ArrError) : IndexerEditorMessage
    data object NameMissing : IndexerEditorMessage
}

/** One prescreened definition preset with its display label. */
data class IndexerPreset(val label: String, val values: Map<String, JsonElement>)

/**
 * State of the add and edit indexer screens. Add mode starts at the schema
 * picker; edit mode loads the resource and jumps straight to the form. The
 * dynamic section renders from [fields] plus the editable [values] map.
 */
data class IndexerEditorUiState(
    val loading: Boolean = true,
    val loadError: ArrError? = null,
    val editing: IndexerResource? = null,
    val schemas: List<IndexerResource> = emptyList(),
    val schemaSearch: String = "",
    val schemaProtocol: ProtocolFilter = ProtocolFilter.ALL,
    val selectedSchema: IndexerResource? = null,
    val name: String = "",
    val nameMissing: Boolean = false,
    val enable: Boolean = true,
    val priority: Int = 25,
    val appProfileId: Int? = null,
    val tagLabels: List<String> = emptyList(),
    val fields: List<Field> = emptyList(),
    val values: Map<String, JsonElement> = emptyMap(),
    val presets: List<IndexerPreset> = emptyList(),
    val selectedCategories: Set<Int> = emptySet(),
    val defaultCategories: List<IndexerCategory> = emptyList(),
    val tagOptions: List<TagResource> = emptyList(),
    val appProfiles: List<AppProfileResource> = emptyList(),
    val showAdvanced: Boolean = false,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val message: IndexerEditorMessage? = null,
) {

    /** True while the schema picker step shows in the add flow. */
    val pickingSchema: Boolean
        get() = editing == null && selectedSchema == null && loadError == null

    /** Prescreened definitions filtered by the picker search and protocol. */
    val filteredSchemas: List<IndexerResource> by lazy(LazyThreadSafetyMode.NONE) {
        val needle = schemaSearch.trim().lowercase()
        schemas.filter { schema ->
            val matchesSearch = needle.isEmpty() ||
                schema.name.orEmpty().lowercase().contains(needle) ||
                schema.definitionName.orEmpty().lowercase().contains(needle) ||
                schema.description.orEmpty().lowercase().contains(needle)
            val matchesProtocol = schemaProtocol == ProtocolFilter.ALL ||
                when (schemaProtocol) {
                    ProtocolFilter.TORRENT ->
                        schema.protocol ==
                            dev.appolon.homelab.arr.api.prowlarr.model.DownloadProtocol.TORRENT

                    ProtocolFilter.USENET ->
                        schema.protocol ==
                            dev.appolon.homelab.arr.api.prowlarr.model.DownloadProtocol.USENET

                    ProtocolFilter.ALL -> true
                }
            matchesSearch && matchesProtocol
        }.sortedBy { it.name.orEmpty().lowercase() }
    }

    /** Fields split into the general dynamic section and seed criteria. */
    val generalFields: List<Field> by lazy(LazyThreadSafetyMode.NONE) {
        fields.filter { FieldForms.isEditable(it) && !FieldForms.isSeedCriteria(it) }
    }

    /** Seed ratio, seed time, and pack seed time when the definition has them. */
    val seedCriteriaFields: List<Field> by lazy(LazyThreadSafetyMode.NONE) {
        fields.filter { FieldForms.isEditable(it) && FieldForms.isSeedCriteria(it) }
    }

    /** Tag labels of the definition, for the info link row. */
    val infoLink: String? get() = (editing ?: selectedSchema)?.infoLink

    val implementationName: String
        get() = editing?.implementationName ?: selectedSchema?.implementationName ?: ""

    val supportsCategories: Boolean
        get() = defaultCategories.isNotEmpty()
}

/**
 * ViewModel behind both the add flow (schema picker then dynamic form) and
 * the edit screen for one indexer. Owns no Android types.
 */
class IndexerEditorViewModel(
    private val repository: ProwlarrIndexersRepository,
    private val editIndexerId: Int? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(IndexerEditorUiState())
    val state: StateFlow<IndexerEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            loadReferenceData()
            if (editIndexerId != null) loadForEdit(editIndexerId) else loadSchemas()
        }
    }

    fun setSchemaSearch(text: String) =
        _state.update { it.copy(schemaSearch = text) }

    fun setSchemaProtocol(filter: ProtocolFilter) =
        _state.update { it.copy(schemaProtocol = filter) }

    /** Enters the form step of the add flow with the chosen definition. */
    fun pickSchema(schema: IndexerResource) = _state.update { s ->
        s.copy(
            selectedSchema = schema,
            fields = schema.fields.orEmpty(),
            values = FieldForms.editableValues(schema.fields),
            presets = schema.presets.orEmpty().mapIndexed { index, preset ->
                IndexerPreset(
                    label = preset.name ?: "Preset ${index + 1}",
                    values = FieldForms.editableValues(preset.fields),
                )
            },
            selectedCategories = IndexerCategories.selectedIds(schema),
            enable = schema.enable,
            priority = schema.priority,
            appProfileId = schema.appProfileId.takeIf { it != 0 },
        )
    }

    fun backToPicker() = _state.update {
        it.copy(
            selectedSchema = null,
            fields = emptyList(),
            values = emptyMap(),
            presets = emptyList(),
            selectedCategories = emptySet(),
            name = "",
            nameMissing = false,
        )
    }

    fun setFieldValue(name: String, value: JsonElement) =
        _state.update { it.copy(values = it.values + (name to value)) }

    fun setName(text: String) =
        _state.update { it.copy(name = text, nameMissing = false) }

    fun setEnable(enabled: Boolean) = _state.update { it.copy(enable = enabled) }

    fun setPriority(priority: Int) = _state.update { it.copy(priority = priority) }

    fun setAppProfileId(id: Int?) = _state.update { it.copy(appProfileId = id) }

    fun toggleTagLabel(label: String) = _state.update { s ->
        val next = if (label in s.tagLabels) s.tagLabels - label else s.tagLabels + label
        s.copy(tagLabels = next)
    }

    /**
     * Toggles a category: parents carry their whole subtree, the same way
     * the Prowlarr category editor behaves.
     */
    fun toggleCategory(categoryId: Int) = _state.update { s ->
        val tree = IndexerCategories.flatten(s.defaultCategories)
            .firstOrNull { it.id == categoryId }
        val subtree: Set<Int> = if (tree == null) {
            setOf(categoryId)
        } else {
            (setOf(tree) + IndexerCategories.flatten(tree.subCategories.orEmpty()))
                .map { it.id }.toSet()
        }
        val next = if (categoryId in s.selectedCategories) {
            s.selectedCategories - subtree
        } else {
            s.selectedCategories + subtree
        }
        s.copy(selectedCategories = next)
    }

    /** Applies one definition preset over the current values. */
    fun applyPreset(index: Int) = _state.update { s ->
        val preset = s.presets.getOrNull(index) ?: return@update s
        s.copy(values = s.values + preset.values)
    }

    fun toggleAdvanced() = _state.update { it.copy(showAdvanced = !it.showAdvanced) }

    /** Saves the form; a successful save closes the screen via the message. */
    fun save() {
        if (_state.value.name.isBlank()) {
            _state.update { it.copy(nameMissing = true, message = IndexerEditorMessage.NameMissing) }
            return
        }
        val resource = buildResource() ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val tagIds = when (val result = repository.ensureTagIds(_state.value.tagLabels)) {
                is ArrResult.Ok -> result.value
                is ArrResult.Err -> {
                    _state.update {
                        it.copy(saving = false, message = IndexerEditorMessage.WriteFailed(result.error))
                    }
                    return@launch
                }
            }
            val withTags = resource.copy(tags = tagIds)
            when (val outcome = repository.saveIndexer(withTags)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        saving = false,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            IndexerEditorMessage.SavedQueued
                        } else {
                            IndexerEditorMessage.Saved
                        },
                    )
                }
                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, message = IndexerEditorMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    /** Probes the current form values without saving them. */
    fun test() {
        val resource = buildResource() ?: return
        _state.update { it.copy(testing = true) }
        viewModelScope.launch {
            when (val result = repository.testIndexer(resource)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(testing = false, message = IndexerEditorMessage.TestPassed)
                }
                is ArrResult.Err -> _state.update {
                    it.copy(testing = false, message = IndexerEditorMessage.TestFailed(result.error))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Retry after a load failure: re-runs whichever load failed. */
    fun reload() {
        val id = editIndexerId
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            if (id != null) loadForEdit(id) else loadSchemas()
        }
    }

    private fun buildResource(): IndexerResource? {
        val s = _state.value
        val base = s.editing ?: s.selectedSchema ?: return null
        val withFields = base.copy(
            fields = FieldForms.applyValues(base.fields, s.values),
            name = s.name.trim(),
            enable = s.enable,
            priority = s.priority,
            appProfileId = s.appProfileId ?: 0,
        )
        return IndexerCategories.withSelectedIds(withFields, s.selectedCategories, s.defaultCategories)
    }

    private suspend fun loadSchemas() {
        when (val result = repository.schemas()) {
            is ArrResult.Ok ->
                _state.update { it.copy(loading = false, schemas = result.value) }
            is ArrResult.Err ->
                _state.update { it.copy(loading = false, loadError = result.error) }
        }
    }

    private suspend fun loadForEdit(id: Int) {
        when (val result = repository.indexer(id)) {
            is ArrResult.Ok -> {
                val indexer = result.value
                val tagLabels = when (val tags = repository.tags()) {
                    is ArrResult.Ok -> indexer.tags.orEmpty().mapNotNull { tagId ->
                        tags.value.firstOrNull { it.id == tagId }?.label
                    }
                    is ArrResult.Err -> emptyList()
                }
                _state.update { s ->
                    s.copy(
                        loading = false,
                        editing = indexer,
                        fields = indexer.fields.orEmpty(),
                        values = FieldForms.editableValues(indexer.fields),
                        name = indexer.name.orEmpty(),
                        enable = indexer.enable,
                        priority = indexer.priority,
                        appProfileId = indexer.appProfileId.takeIf { it != 0 },
                        selectedCategories = IndexerCategories.selectedIds(indexer),
                        tagLabels = tagLabels,
                    )
                }
            }
            is ArrResult.Err ->
                _state.update { it.copy(loading = false, loadError = result.error) }
        }
    }

    private suspend fun loadReferenceData() {
        when (val categories = repository.defaultCategories()) {
            is ArrResult.Ok ->
                _state.update { it.copy(defaultCategories = categories.value) }
            is ArrResult.Err -> Unit
        }
        when (val tags = repository.tags()) {
            is ArrResult.Ok -> _state.update { it.copy(tagOptions = tags.value) }
            is ArrResult.Err -> Unit
        }
        when (val profiles = repository.appProfiles()) {
            is ArrResult.Ok -> _state.update { it.copy(appProfiles = profiles.value) }
            is ArrResult.Err -> Unit
        }
    }
}
