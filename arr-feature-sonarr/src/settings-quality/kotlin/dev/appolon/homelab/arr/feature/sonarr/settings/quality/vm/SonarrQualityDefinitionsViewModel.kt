package dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionLimitsResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.data.SonarrQualityRepository
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
 * Editable row of the quality definitions table. Size bounds are strings so
 * partially typed numbers never crash the editor; parsing happens at
 * validation time. A blank max or preferred means unlimited / unset, which
 * is how the server models null.
 */
data class QualityDefinitionDraft(
    val id: Int = 0,
    val qualityName: String = "",
    val title: String = "",
    val weight: Int = 0,
    val minSize: String = "",
    val maxSize: String = "",
    val preferredSize: String = "",
) {
    constructor(resource: QualityDefinitionResource) : this(
        id = resource.id,
        qualityName = resource.quality.name.orEmpty(),
        title = resource.title.orEmpty(),
        weight = resource.weight,
        minSize = resource.minSize?.toString().orEmpty(),
        maxSize = resource.maxSize?.toString().orEmpty(),
        preferredSize = resource.preferredSize?.toString().orEmpty(),
    )

    /**
     * Maps back onto the wire resource. Blank sizes become null so the
     * server reads them as unlimited; a malformed non-blank number falls
     * back to the loaded value rather than silently dropping the bound.
     */
    fun applyOnto(resource: QualityDefinitionResource): QualityDefinitionResource = resource.copy(
        title = title,
        weight = weight,
        minSize = parseSize(minSize, resource.minSize),
        maxSize = parseSize(maxSize, resource.maxSize),
        preferredSize = parseSize(preferredSize, resource.preferredSize),
    )

    private fun parseSize(field: String, fallback: Double?): Double? = when {
        field.isBlank() -> null
        else -> field.toDoubleOrNull() ?: fallback
    }

    /** Parses the min bound; null when the field holds something non-numeric. */
    fun parsedMin(): Double? = minSize.toDoubleOrNull()

    /** Parses the max bound; null parses as unlimited when the field is blank. */
    fun parsedMax(): Double? = when {
        maxSize.isBlank() -> null
        else -> maxSize.toDoubleOrNull()
    }

    /** Parses the preferred bound; blank means unset. */
    fun parsedPreferred(): Double? = when {
        preferredSize.isBlank() -> null
        else -> preferredSize.toDoubleOrNull()
    }
}

/**
 * One row-level validation problem, mapped to a string by the screen.
 */
enum class QualityDefinitionFieldError {
    MIN_NOT_A_NUMBER,
    MAX_NOT_A_NUMBER,
    PREFERRED_NOT_A_NUMBER,
    MIN_ABOVE_MAX,
    MIN_NEGATIVE,
    PREFERRED_ABOVE_MAX,
}

/** Content payload of the quality definitions LCE state. */
data class SonarrQualityDefinitionsContent(
    val drafts: List<QualityDefinitionDraft> = emptyList(),
    val limits: QualityDefinitionLimitsResource? = null,
    val errors: Map<Int, Set<QualityDefinitionFieldError>> = emptyMap(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val pendingActions: Int = 0,
)

/** Transient save results, mapped to strings by the screen. */
enum class QualityDefinitionsNotice { SAVED, SAVED_QUEUED }

/**
 * Quality definitions editor state. Rows load cache-first; edits stay local
 * until save, which validates every row and PUTs the whole table through
 * the bulk endpoint. Offline saves apply optimistically and queue.
 */
class SonarrQualityDefinitionsViewModel(
    private val repository: SonarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val drafts: List<QualityDefinitionDraft> = emptyList(),
        val limits: QualityDefinitionLimitsResource? = null,
        val errors: Map<Int, Set<QualityDefinitionFieldError>> = emptyMap(),
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val pendingActions: Int = 0,
        val notice: QualityDefinitionsNotice? = null,
        val writeError: ArrError? = null,
    )

    private var loadedRows: List<QualityDefinitionResource> = emptyList()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SonarrQualityDefinitionsContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    SonarrQualityDefinitionsContent(
                        drafts = s.drafts,
                        limits = s.limits,
                        errors = s.errors,
                        dirty = s.dirty,
                        saving = s.saving,
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeQualityDefinitions().collect { rows ->
                if (rows.isNotEmpty()) {
                    loadedRows = rows.sortedBy { it.weight }
                    // A background refresh never camps on unsaved edits.
                    _state.update { s ->
                        if (s.dirty) {
                            s.copy(loaded = true, loadError = null)
                        } else {
                            s.copy(
                                loaded = true,
                                loadError = null,
                                drafts = loadedRows.map(::QualityDefinitionDraft),
                            )
                        }
                    }
                }
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
            val refreshed = repository.refreshIfStale()
            val limits = repository.cachedQualityDefinitionLimits()
            when (refreshed) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, limits = limits) }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (!it.loaded) refreshed.error else null,
                        writeError = if (it.loaded) refreshed.error else null,
                        refreshing = false,
                        limits = limits,
                    )
                }
            }
        }
    }

    fun edit(transform: (QualityDefinitionDraft) -> QualityDefinitionDraft) {
        _state.update { s ->
            s.copy(drafts = s.drafts.map(transform), dirty = true)
        }
    }

    fun editRow(definitionId: Int, transform: (QualityDefinitionDraft) -> QualityDefinitionDraft) {
        edit { if (it.id == definitionId) transform(it) else it }
    }

    /** Revalidates every row; the save button and inline errors read this. */
    fun validate(drafts: List<QualityDefinitionDraft>): Map<Int, Set<QualityDefinitionFieldError>> {
        val errors = mutableMapOf<Int, MutableSet<QualityDefinitionFieldError>>()
        fun add(row: QualityDefinitionDraft, error: QualityDefinitionFieldError) {
            errors.getOrPut(row.id) { mutableSetOf() }.add(error)
        }
        drafts.forEach { row ->
            val min = row.parsedMin()
            val max = row.parsedMax()
            val preferred = row.parsedPreferred()
            if (row.minSize.isNotBlank() && min == null) add(row, QualityDefinitionFieldError.MIN_NOT_A_NUMBER)
            if (row.maxSize.isNotBlank() && max == null) add(row, QualityDefinitionFieldError.MAX_NOT_A_NUMBER)
            if (row.preferredSize.isNotBlank() && preferred == null) {
                add(row, QualityDefinitionFieldError.PREFERRED_NOT_A_NUMBER)
            }
            if (min != null && min < 0) add(row, QualityDefinitionFieldError.MIN_NEGATIVE)
            if (min != null && max != null && min > max) add(row, QualityDefinitionFieldError.MIN_ABOVE_MAX)
            if (preferred != null && max != null && preferred > max) {
                add(row, QualityDefinitionFieldError.PREFERRED_ABOVE_MAX)
            }
        }
        return errors
    }

    fun save() {
        val current = _state.value
        if (!current.dirty || current.saving) return
        val errors = validate(current.drafts)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(errors = emptyMap()) }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val byId = loadedRows.associateBy { it.id }
            val payload = current.drafts.map { draft ->
                val original = byId[draft.id]
                    ?: QualityDefinitionResource(id = draft.id, weight = draft.weight)
                draft.applyOnto(original)
            }
            when (val result = repository.saveQualityDefinitions(payload)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            QualityDefinitionsNotice.SAVED_QUEUED
                        } else {
                            QualityDefinitionsNotice.SAVED
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
