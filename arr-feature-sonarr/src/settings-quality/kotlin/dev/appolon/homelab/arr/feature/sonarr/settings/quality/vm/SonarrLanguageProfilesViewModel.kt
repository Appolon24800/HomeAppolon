package dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileItemResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
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
 * Editable draft of a language profile, the table Sonarr has and Radarr
 * does not. Each row toggles one language; the cutoff picks the language
 * upgrades stop at, and upgradeAllowed groups the per-language upgrades
 * into one step instead of walking every language.
 */
data class LanguageProfileDraft(
    val profileId: Int = 0,
    val name: String = "",
    val upgradeAllowed: Boolean = true,
    /** The language upgrades stop at; only allowed languages are valid. */
    val cutoff: Language = Language(),
    val items: List<LanguageProfileItemResource> = emptyList(),
    /** True when this draft edits an existing profile rather than the schema. */
    val isExisting: Boolean = false,
) {
    /** Languages currently allowed, in list order; the cutoff picker lists these. */
    fun allowedLanguages(): List<Language> = items.filter { it.allowed }.map { it.language }

    /**
     * Builds the wire resource. The cutoff falls back to the first allowed
     * language when the chosen one was switched off, because the server
     * rejects a cutoff outside the allowed set.
     */
    fun toResource(): LanguageProfileResource {
        val allowed = allowedLanguages()
        val effectiveCutoff = when {
            allowed.any { it.id == cutoff.id } -> cutoff
            else -> allowed.firstOrNull() ?: cutoff
        }
        return LanguageProfileResource(
            id = profileId,
            name = name,
            upgradeAllowed = upgradeAllowed,
            cutoff = effectiveCutoff,
            languages = items,
        )
    }

    companion object {
        /** Fills a draft from a loaded profile or the blank schema. */
        fun from(resource: LanguageProfileResource, isExisting: Boolean): LanguageProfileDraft =
            LanguageProfileDraft(
                profileId = resource.id,
                name = resource.name.orEmpty(),
                upgradeAllowed = resource.upgradeAllowed,
                cutoff = resource.cutoff,
                items = resource.languages.orEmpty(),
                isExisting = isExisting,
            )
    }
}

/** Content payload of the language profiles list LCE state. */
data class SonarrLanguageProfilesContent(
    val profiles: List<LanguageProfileResource> = emptyList(),
    val pendingActions: Int = 0,
)

/** Content payload of the language profile builder LCE state. */
data class SonarrLanguageProfileEditorContent(
    val draft: LanguageProfileDraft = LanguageProfileDraft(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val pendingActions: Int = 0,
)

/** Transient list-screen results, mapped to strings by the screen. */
enum class LanguageProfilesNotice { CREATED, UPDATED, DELETED, QUEUED }

/**
 * Language profiles list screen state: cache-first rows, create through the
 * schema-driven builder, delete with confirmation. Writes queue offline
 * with optimistic cache updates.
 */
class SonarrLanguageProfilesViewModel(
    private val repository: SonarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val profiles: List<LanguageProfileResource> = emptyList(),
        val pendingActions: Int = 0,
        val notice: LanguageProfilesNotice? = null,
        val writeError: ArrError? = null,
        /** Profile id pending delete confirmation. */
        val confirmDeleteId: Int? = null,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SonarrLanguageProfilesContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    SonarrLanguageProfilesContent(s.profiles.sortedBy { it.name }, s.pendingActions),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeLanguageProfiles().collect { profiles ->
                _state.update { it.copy(loaded = true, loadError = null, profiles = profiles) }
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

    fun requestDelete(profileId: Int?) {
        _state.update { it.copy(confirmDeleteId = profileId) }
    }

    fun deleteConfirmed() {
        val id = _state.value.confirmDeleteId ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.deleteLanguageProfile(id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        confirmDeleteId = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            LanguageProfilesNotice.QUEUED
                        } else {
                            LanguageProfilesNotice.DELETED
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
 * Schema-driven language profile builder state. New profiles start from the
 * server schema (every language listed, none allowed); existing ones load
 * from the cache. Saves POST for new profiles and PUT for existing ones.
 */
class SonarrLanguageProfileEditorViewModel(
    private val profileId: Int?,
    private val repository: SonarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val draft: LanguageProfileDraft = LanguageProfileDraft(),
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val pendingActions: Int = 0,
        val savedNotice: Boolean = false,
        val savedQueued: Boolean = false,
        val writeError: ArrError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SonarrLanguageProfileEditorContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    SonarrLanguageProfileEditorContent(
                        draft = s.draft,
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
            repository.queuedCount().collect { pending ->
                _state.update { it.copy(pendingActions = pending) }
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            repository.refreshIfStale()
            val existing = profileId?.let { id ->
                repository.cachedLanguageProfiles().firstOrNull { it.id == id }
            }
            if (existing != null) {
                _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        draft = LanguageProfileDraft.from(existing, isExisting = true),
                    )
                }
                return@launch
            }
            if (profileId != null) {
                // Editing a row that vanished (deleted on the server or a
                // stale link) surfaces as an error rather than a blank form.
                _state.update {
                    it.copy(loadError = ArrError.Http(404, null))
                }
                return@launch
            }
            when (val schema = repository.refreshLanguageProfileSchema()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        draft = LanguageProfileDraft.from(schema.value, isExisting = false),
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(loadError = schema.error) }
            }
        }
    }

    fun refresh() {
        load()
    }

    fun edit(transform: (LanguageProfileDraft) -> LanguageProfileDraft) {
        _state.update { it.copy(draft = transform(it.draft), dirty = true) }
    }

    fun setName(name: String) {
        edit { it.copy(name = name) }
    }

    fun setUpgradeAllowed(allowed: Boolean) {
        edit { it.copy(upgradeAllowed = allowed) }
    }

    /** Toggles one language's allowed state. */
    fun toggleLanguage(item: LanguageProfileItemResource) {
        edit { draft ->
            draft.copy(
                items = draft.items.map {
                    if (it.language.id == item.language.id) it.copy(allowed = !it.allowed) else it
                },
            )
        }
    }

    fun setCutoff(language: Language) {
        edit { it.copy(cutoff = language) }
    }

    fun save() {
        val current = _state.value
        if (!current.dirty || current.saving) return
        if (current.draft.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.saveLanguageProfile(current.draft.toResource())) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        savedNotice = result.value is WriteOutcome.Synced,
                        savedQueued = result.value is WriteOutcome.Queued,
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, writeError = result.error)
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
