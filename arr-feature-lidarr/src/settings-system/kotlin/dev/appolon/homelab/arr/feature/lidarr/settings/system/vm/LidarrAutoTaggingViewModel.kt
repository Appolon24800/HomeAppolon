package dev.appolon.homelab.arr.feature.lidarr.settings.system.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.lidarr.model.AutoTaggingSpecificationSchema
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.system.data.LidarrSettingsSystemRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One list row: a rule with its tag labels and condition count. */
data class LidarrAutoTaggingRow(
    val rule: AutoTaggingResource,
    val name: String,
    val tagLabels: List<String>,
    val conditionCount: Int,
    val queuedForRemoval: Boolean,
)

/** Content payload of the auto tagging list LCE state. */
data class LidarrAutoTaggingContent(
    val rows: List<LidarrAutoTaggingRow>,
    val pendingActions: Int,
)

/** Transient action results, mapped to strings by the screen. */
enum class AutoTaggingNotice { SAVED, SAVED_QUEUED, DELETED, DELETED_QUEUED }

/**
 * Auto tagging list state. Rules load cache-first; delete confirms first;
 * offline writes queue with per-row queued badges. Editing happens on the
 * editor route backed by [LidarrAutoTaggingEditorViewModel].
 */
class LidarrAutoTaggingViewModel(
    private val repository: LidarrSettingsSystemRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val rules: List<AutoTaggingResource> = emptyList(),
        val tags: List<TagResource> = emptyList(),
        val queuedRemovedIds: Set<Int> = emptySet(),
        val pendingActions: Int = 0,
        val confirmDeleteFor: AutoTaggingResource? = null,
        val busy: Boolean = false,
        val notice: AutoTaggingNotice? = null,
        val writeError: ArrError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrAutoTaggingContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.rules.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.rules.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    LidarrAutoTaggingContent(
                        rows = s.rules
                            .sortedBy { it.name }
                            .map { rule -> rule.toRow(s.tags, rule.id in s.queuedRemovedIds) },
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeAutoTagging().collect { rules ->
                _state.update { it.copy(loaded = true, loadError = null, rules = rules) }
            }
        }
        viewModelScope.launch {
            repository.observeTags().collect { tags -> _state.update { it.copy(tags = tags) } }
        }
        viewModelScope.launch {
            repository.queuedRemovedAutoTaggingIds().collect { ids ->
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
            when (val result = repository.refreshAutoTaggingIfStale()) {
                is ArrResult.Ok -> {
                    repository.refreshTagsIfStale()
                    _state.update { it.copy(refreshing = false) }
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (it.rules.isEmpty()) result.error else null,
                        writeError = if (it.rules.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun requestDelete(rule: AutoTaggingResource) {
        _state.update { it.copy(confirmDeleteFor = rule) }
    }

    fun deleteCancelled() {
        _state.update { it.copy(confirmDeleteFor = null) }
    }

    fun deleteConfirmed() {
        val target = _state.value.confirmDeleteFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, confirmDeleteFor = null) }
            when (val result = repository.deleteAutoTagging(target.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            AutoTaggingNotice.DELETED_QUEUED
                        } else {
                            AutoTaggingNotice.DELETED
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

    private fun AutoTaggingResource.toRow(tags: List<TagResource>, queuedForRemoval: Boolean) =
        LidarrAutoTaggingRow(
            rule = this,
            name = name.orEmpty(),
            tagLabels = tags.filter { it.id in (this.tags ?: emptyList()) }.map { it.label.orEmpty() },
            conditionCount = specifications?.size ?: 0,
            queuedForRemoval = queuedForRemoval,
        )
}

/**
 * Editable condition of an auto tagging rule. The wire specification is
 * kept verbatim and [fieldValues] holds the edits keyed by field name, so
 * unknown or advanced fields round-trip untouched.
 */
data class AutoTaggingConditionDraft(
    val specification: AutoTaggingSpecificationSchema,
    val fieldValues: Map<String, kotlinx.serialization.json.JsonElement>,
)

/**
 * Editable auto tagging rule: name, the remove-automatically toggle, the
 * applied tags, and the condition list. Conditions added from the catalog
 * start at their default field values.
 */
data class AutoTaggingDraft(
    val name: String = "",
    val removeTagsAutomatically: Boolean = false,
    val tagIds: Set<Int> = emptySet(),
    val conditions: List<AutoTaggingConditionDraft> = emptyList(),
) {
    constructor(rule: AutoTaggingResource) : this(
        name = rule.name.orEmpty(),
        removeTagsAutomatically = rule.removeTagsAutomatically,
        tagIds = (rule.tags ?: emptyList()).toSet(),
        conditions = (rule.specifications ?: emptyList()).map { spec ->
            AutoTaggingConditionDraft(
                specification = spec,
                fieldValues = spec.fields.orEmpty()
                    .filter { it.name != null }
                    .associate { field ->
                        field.name!! to (field.value ?: kotlinx.serialization.json.JsonNull)
                    },
            )
        },
    )

    /** Maps the draft back onto the wire rule, writing edits into the fields. */
    fun applyOnto(rule: AutoTaggingResource): AutoTaggingResource = rule.copy(
        name = name.trim(),
        removeTagsAutomatically = removeTagsAutomatically,
        tags = tagIds,
        specifications = conditions.map { draft ->
            draft.specification.copy(
                fields = draft.specification.fields.orEmpty().map { field ->
                    val key = field.name ?: return@map field
                    if (draft.fieldValues.containsKey(key)) {
                        field.copy(value = draft.fieldValues.getValue(key))
                    } else {
                        field
                    }
                },
            )
        },
    )
}

/**
 * Auto tagging editor state. The rule loads cache-first (a null [ruleId]
 * starts a blank draft), the static condition catalog drives the
 * add-condition picker, and save POSTs or PUTs through the repository with
 * offline queueing.
 */
class LidarrAutoTaggingEditorViewModel(
    private val ruleId: Int?,
    private val repository: LidarrSettingsSystemRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val isNew: Boolean = true,
        val draft: AutoTaggingDraft = AutoTaggingDraft(),
        val original: AutoTaggingResource? = null,
        val tags: List<TagResource> = emptyList(),
        /** Condition catalog for the add picker; the static list until the schema loads. */
        val conditions: List<AutoTaggingSpecificationSchema> =
            dev.appolon.homelab.arr.feature.lidarr.settings.system.data.LidarrAutoTaggingConditions.entries,
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val pendingActions: Int = 0,
        val nameError: Boolean = false,
        val notice: AutoTaggingNotice? = null,
        val writeError: ArrError? = null,
    )

    private val _state = MutableStateFlow(UiState(isNew = ruleId == null))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAutoTagging().collect { list ->
                val current = ruleId?.let { id -> list.firstOrNull { it.id == id } }
                if (ruleId == null || current != null) {
                    _state.update { s ->
                        if (s.dirty) {
                            s.copy(loaded = true, loadError = null, original = current ?: s.original)
                        } else if (current != null) {
                            s.copy(
                                loaded = true,
                                loadError = null,
                                original = current,
                                draft = AutoTaggingDraft(current),
                            )
                        } else {
                            s.copy(loaded = true)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.observeTags().collect { tags -> _state.update { it.copy(tags = tags) } }
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
            when (val result = repository.refreshAutoTaggingIfStale()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(refreshing = false, loaded = it.loaded || !it.isNew)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (!it.loaded && !it.isNew) result.error else null,
                        refreshing = false,
                    )
                }
            }
            // The live schema refines the picker; failures keep the static catalog.
            when (val schema = repository.autoTaggingConditions()) {
                is ArrResult.Ok -> _state.update {
                    if (schema.value.isNotEmpty()) it.copy(conditions = schema.value) else it
                }

                is ArrResult.Err -> Unit
            }
        }
    }

    fun edit(transform: (AutoTaggingDraft) -> AutoTaggingDraft) {
        _state.update { it.copy(draft = transform(it.draft), dirty = true) }
    }

    /** Toggles one applied tag chip. */
    fun toggleTag(tagId: Int) {
        edit { draft ->
            draft.copy(tagIds = if (tagId in draft.tagIds) draft.tagIds - tagId else draft.tagIds + tagId)
        }
    }

    /** Adds a condition seeded from a catalog entry. */
    fun addCondition(catalogEntry: AutoTaggingSpecificationSchema) {
        edit { draft ->
            draft.copy(
                conditions = draft.conditions + AutoTaggingConditionDraft(
                    specification = catalogEntry.copy(id = 0),
                    fieldValues = catalogEntry.fields.orEmpty()
                        .filter { it.name != null }
                        .associate { field ->
                            field.name!! to (field.value ?: kotlinx.serialization.json.JsonNull)
                        },
                ),
            )
        }
    }

    fun removeCondition(index: Int) {
        edit { draft ->
            draft.copy(conditions = draft.conditions.filterIndexed { i, _ -> i != index })
        }
    }

    fun editCondition(index: Int, transform: (AutoTaggingConditionDraft) -> AutoTaggingConditionDraft) {
        edit { draft ->
            draft.copy(
                conditions = draft.conditions.mapIndexed { i, condition ->
                    if (i == index) transform(condition) else condition
                },
            )
        }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        if (current.draft.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        _state.update { it.copy(nameError = false) }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val base = current.original ?: AutoTaggingResource(id = 0)
            val result = repository.saveAutoTagging(current.draft.applyOnto(base))
            when (result) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            AutoTaggingNotice.SAVED_QUEUED
                        } else {
                            AutoTaggingNotice.SAVED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, notice = null, writeError = result.error)
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
