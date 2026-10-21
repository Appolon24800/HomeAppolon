package dev.appolon.homelab.arr.feature.radarr.settings.quality.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.quality.data.CustomFormatCondition
import dev.appolon.homelab.arr.feature.radarr.settings.quality.data.CustomFormatConditionKind
import dev.appolon.homelab.arr.feature.radarr.settings.quality.data.CustomFormatConditions
import dev.appolon.homelab.arr.feature.radarr.settings.quality.data.RadarrQualityRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editable draft of one custom format. */
data class CustomFormatDraft(
    val formatId: Int = 0,
    val name: String = "",
    val includeWhenRenaming: Boolean = false,
    val conditions: List<CustomFormatCondition> = emptyList(),
    val isExisting: Boolean = false,
    /**
     * Conditions the server sent that this builder cannot edit (an
     * implementation added after this build). Carried through saves
     * unchanged so a round trip never loses them.
     */
    val passthroughConditions: List<dev.appolon.homelab.arr.api.radarr.model.CustomFormatSpecificationSchema> =
        emptyList(),
) {
    fun toResource(): CustomFormatResource = CustomFormatResource(
        id = formatId,
        name = name,
        includeCustomFormatWhenRenaming = includeWhenRenaming,
        specifications = conditions.map(CustomFormatConditions::toSchema) + passthroughConditions,
    )

    companion object {
        fun from(resource: CustomFormatResource, isExisting: Boolean): CustomFormatDraft {
            val editable = mutableListOf<CustomFormatCondition>()
            val passthrough = mutableListOf<dev.appolon.homelab.arr.api.radarr.model.CustomFormatSpecificationSchema>()
            resource.specifications.orEmpty().forEach { schema ->
                val condition = CustomFormatConditions.fromSchema(schema)
                if (condition != null) editable += condition else passthrough += schema
            }
            return CustomFormatDraft(
                formatId = resource.id,
                name = resource.name.orEmpty(),
                includeWhenRenaming = resource.includeCustomFormatWhenRenaming ?: false,
                conditions = editable,
                passthroughConditions = passthrough,
                isExisting = isExisting,
            )
        }
    }
}

/** Per-profile score row shown in the custom format editor. */
data class FormatScoreRow(
    val profile: QualityProfileResource,
    val score: Int,
    /** True when the profile already lists this format. */
    val attached: Boolean,
)

/** Content payload of the custom formats list LCE state. */
data class RadarrCustomFormatsContent(
    val formats: List<CustomFormatResource> = emptyList(),
    val pendingActions: Int = 0,
)

/** Content payload of the custom format builder LCE state. */
data class RadarrCustomFormatEditorContent(
    val draft: CustomFormatDraft = CustomFormatDraft(),
    val scoreRows: List<FormatScoreRow> = emptyList(),
    val regexSample: String = "",
    val regexPattern: String = "",
    val regexResult: Boolean? = null,
    val regexPatternInvalid: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val pendingActions: Int = 0,
)

/** Transient list-screen results, mapped to strings by the screen. */
enum class CustomFormatsNotice { CREATED, UPDATED, DELETED, QUEUED }

/**
 * Custom formats list screen state: cache-first rows, create and delete.
 * Writes queue offline with optimistic cache updates.
 */
class RadarrCustomFormatsViewModel(
    private val repository: RadarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val formats: List<CustomFormatResource> = emptyList(),
        val pendingActions: Int = 0,
        val notice: CustomFormatsNotice? = null,
        val writeError: ArrError? = null,
        val confirmDeleteId: Int? = null,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrCustomFormatsContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    RadarrCustomFormatsContent(s.formats.sortedBy { it.name }, s.pendingActions),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeCustomFormats().collect { formats ->
                _state.update { it.copy(loaded = true, loadError = null, formats = formats) }
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

    fun requestDelete(formatId: Int?) {
        _state.update { it.copy(confirmDeleteId = formatId) }
    }

    fun deleteConfirmed() {
        val id = _state.value.confirmDeleteId ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.deleteCustomFormat(id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        confirmDeleteId = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            CustomFormatsNotice.QUEUED
                        } else {
                            CustomFormatsNotice.DELETED
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
 * Custom format builder state: name, include-when-renaming, the six
 * condition editors, a regex tester for release-name conditions, and score
 * assignment rows that write the score into each quality profile.
 */
class RadarrCustomFormatEditorViewModel(
    private val formatId: Int?,
    private val repository: RadarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val draft: CustomFormatDraft = CustomFormatDraft(),
        val scoreRows: List<FormatScoreRow> = emptyList(),
        /** Original scores, to save only the profiles the user actually changed. */
        val originalScores: Map<Int, Int> = emptyMap(),
        val regexSample: String = "",
        val regexPattern: String = "",
        val regexResult: Boolean? = null,
        val regexPatternInvalid: Boolean = false,
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val pendingActions: Int = 0,
        val savedNotice: Boolean = false,
        val savedQueued: Boolean = false,
        val writeError: ArrError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrCustomFormatEditorContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    RadarrCustomFormatEditorContent(
                        draft = s.draft,
                        scoreRows = s.scoreRows,
                        regexSample = s.regexSample,
                        regexPattern = s.regexPattern,
                        regexResult = s.regexResult,
                        regexPatternInvalid = s.regexPatternInvalid,
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
            repository.observeQualityProfiles().collect { profiles ->
                _state.update { s ->
                    val attached = s.draft.formatId
                    s.copy(
                        scoreRows = scoreRowsFor(attached, profiles),
                        originalScores = if (s.originalScores.isEmpty() && attached > 0) {
                            originalScoresFor(attached, profiles)
                        } else {
                            s.originalScores
                        },
                    )
                }
            }
        }
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
            if (formatId != null) {
                val existing = repository.cachedCustomFormats().firstOrNull { it.id == formatId }
                if (existing == null) {
                    _state.update { it.copy(loadError = ArrError.Http(404, null)) }
                    return@launch
                }
                val profiles = repository.cachedQualityProfiles()
                _state.update { s ->
                    val draft = CustomFormatDraft.from(existing, isExisting = true)
                    s.copy(
                        loaded = true,
                        loadError = null,
                        draft = draft,
                        // The profile collector may have emitted before the
                        // draft existed; resolve the score rows against the
                        // cached profiles now that the format id is known.
                        scoreRows = scoreRowsFor(draft.formatId, profiles),
                        originalScores = originalScoresFor(draft.formatId, profiles),
                    )
                }
            } else {
                _state.update { it.copy(loaded = true, loadError = null) }
            }
        }
    }

    private fun scoreRowsFor(
        formatId: Int,
        profiles: List<QualityProfileResource>,
    ): List<FormatScoreRow> = if (formatId <= 0) {
        emptyList()
    } else {
        profiles.sortedBy { it.name }.map { profile ->
            val item = profile.formatItems.orEmpty().firstOrNull { it.format == formatId }
            FormatScoreRow(profile = profile, score = item?.score ?: 0, attached = item != null)
        }
    }

    private fun originalScoresFor(
        formatId: Int,
        profiles: List<QualityProfileResource>,
    ): Map<Int, Int> = if (formatId <= 0) {
        emptyMap()
    } else {
        profiles.associate { profile ->
            profile.id to (
                profile.formatItems.orEmpty().firstOrNull { it.format == formatId }?.score ?: 0
                )
        }
    }

    fun refresh() {
        load()
    }

    fun edit(transform: (CustomFormatDraft) -> CustomFormatDraft) {
        _state.update { it.copy(draft = transform(it.draft), dirty = true) }
    }

    fun addCondition(kind: CustomFormatConditionKind) {
        edit { it.copy(conditions = it.conditions + CustomFormatCondition(kind = kind)) }
    }

    fun editCondition(
        condition: CustomFormatCondition,
        transform: (CustomFormatCondition) -> CustomFormatCondition,
    ) {
        edit { draft ->
            draft.copy(
                conditions = draft.conditions.map { if (it == condition) transform(it) else it },
            )
        }
    }

    fun removeCondition(condition: CustomFormatCondition) {
        edit { it.copy(conditions = it.conditions - condition) }
    }

    /** Sets the regex tester inputs; the result updates live per keystroke. */
    fun setRegexTester(pattern: String, sample: String) {
        val result = CustomFormatConditions.regexMatch(pattern, sample)
        _state.update {
            it.copy(
                regexPattern = pattern,
                regexSample = sample,
                regexResult = result,
                regexPatternInvalid = pattern.isNotBlank() && result == null && sample.isNotBlank(),
            )
        }
    }

    fun setScore(profileId: Int, score: Int) {
        _state.update { s ->
            s.copy(
                scoreRows = s.scoreRows.map { row ->
                    if (row.profile.id == profileId) row.copy(score = score, attached = true) else row
                },
                dirty = true,
            )
        }
    }

    fun save() {
        val current = _state.value
        if (!current.dirty || current.saving) return
        if (current.draft.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val formatResult = repository.saveCustomFormat(current.draft.toResource())
            when (formatResult) {
                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, writeError = formatResult.error)
                }

                is ArrResult.Ok -> {
                    // New formats learn their server id only after the save,
                    // so score rows re-resolve against the saved row.
                    val savedId = current.draft.formatId.takeIf { id -> id > 0 }
                        ?: repository.cachedCustomFormats()
                            .firstOrNull { row -> row.name == current.draft.name }
                            ?.id
                    var queued = formatResult.value is WriteOutcome.Queued
                    var firstError: ArrError? = null
                    if (savedId != null) {
                        for (row in current.scoreRows) {
                            val original = current.originalScores[row.profile.id] ?: 0
                            if (row.score == original) continue
                            when (
                                val scoreResult = repository.assignFormatScore(
                                    row.profile.copy(formatItems = row.profile.formatItems),
                                    savedId,
                                    row.score,
                                )
                            ) {
                                is ArrResult.Ok -> queued = queued || scoreResult.value is WriteOutcome.Queued

                                is ArrResult.Err -> if (firstError == null) firstError = scoreResult.error
                            }
                        }
                    }
                    _state.update {
                        it.copy(
                            saving = false,
                            dirty = firstError != null,
                            savedNotice = firstError == null && !queued,
                            savedQueued = firstError == null && queued,
                            writeError = firstError,
                        )
                    }
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
