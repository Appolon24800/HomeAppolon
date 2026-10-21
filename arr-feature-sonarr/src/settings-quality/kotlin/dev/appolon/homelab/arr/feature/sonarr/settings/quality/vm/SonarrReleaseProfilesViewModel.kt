package dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseProfileResource
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Editable draft of a release profile. Sonarr's wire stores must-contain
 * and must-not-contain as raw JSON that is either a comma-joined string or
 * an array depending on the server version; the editor works on clean term
 * lists and converts on load and save. Sonarr-specific fields (air date
 * restriction, season pack rule, excluded tags) are preserved from the
 * loaded row because they belong to screens this group does not own.
 */
data class ReleaseProfileDraft(
    val profileId: Int = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val mustContain: List<String> = emptyList(),
    val mustNotContain: List<String> = emptyList(),
    val indexerId: String = "0",
    val isExisting: Boolean = false,
) {
    fun toResource(original: ReleaseProfileResource?): ReleaseProfileResource = ReleaseProfileResource(
        id = profileId,
        name = name,
        enabled = enabled,
        required = joinTerms(mustContain),
        ignored = joinTerms(mustNotContain),
        indexerId = indexerId.toIntOrNull() ?: original?.indexerId ?: 0,
        airDateRestriction = original?.airDateRestriction ?: false,
        airDateGracePeriod = original?.airDateGracePeriod ?: 0,
        allowSeasonPackWithoutAllEpisodesAired =
        original?.allowSeasonPackWithoutAllEpisodesAired ?: false,
        // Tags live in the tags screen; preserve whatever the server had.
        tags = original?.tags,
        excludedTags = original?.excludedTags,
    )

    companion object {
        /**
         * Splits the wire's term blob into clean terms. Arrays contribute
         * one term per element; strings split on commas and newlines because
         * Sonarr versions differ on the separator.
         */
        fun splitTerms(raw: JsonElement?): List<String> = when (raw) {
            is JsonArray -> raw.flatMap { element -> splitTerms(element) }
            is JsonPrimitive -> raw.content
                .split(',', '\n', '\r')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            else -> emptyList()
        }

        /** Splits the editor's raw text (one term per line) into clean terms. */
        fun splitText(text: String): List<String> = text
            .split(',', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        /**
         * Encodes terms back as a JSON array. The spec allows either shape;
         * arrays are unambiguous, and terms containing commas survive the
         * round trip.
         */
        fun joinTerms(terms: List<String>): JsonElement = JsonArray(
            terms
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { term -> JsonPrimitive(term) },
        )

        fun from(resource: ReleaseProfileResource): ReleaseProfileDraft = ReleaseProfileDraft(
            profileId = resource.id,
            name = resource.name.orEmpty(),
            enabled = resource.enabled,
            mustContain = splitTerms(resource.required),
            mustNotContain = splitTerms(resource.ignored),
            indexerId = resource.indexerId.toString(),
            isExisting = true,
        )
    }
}

/** Content payload of the release profiles LCE state. */
data class SonarrReleaseProfilesContent(
    val profiles: List<ReleaseProfileResource> = emptyList(),
    val pendingActions: Int = 0,
)

/** Transient results, mapped to strings by the screen. */
enum class ReleaseProfilesNotice { CREATED, UPDATED, DELETED, QUEUED }

/**
 * Release profiles screen state: ordered rows opening an editor with the
 * must-contain and must-not-contain term lists, the indexer restriction,
 * and the enabled toggle. Writes queue offline with optimistic updates.
 */
class SonarrReleaseProfilesViewModel(
    private val repository: SonarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val profiles: List<ReleaseProfileResource> = emptyList(),
        val pendingActions: Int = 0,
        val notice: ReleaseProfilesNotice? = null,
        val writeError: ArrError? = null,
        val confirmDeleteId: Int? = null,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SonarrReleaseProfilesContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    SonarrReleaseProfilesContent(s.profiles.sortedBy { it.name }, s.pendingActions),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeReleaseProfiles().collect { profiles ->
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

    /** Saves a draft: POST when new, PUT when it edits a saved row. */
    fun save(draft: ReleaseProfileDraft) {
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val original = _state.value.profiles.firstOrNull { it.id == draft.profileId }
            val isNew = !draft.isExisting || draft.profileId <= 0
            when (val result = repository.saveReleaseProfile(draft.toResource(original))) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        notice = when {
                            result.value is WriteOutcome.Queued -> ReleaseProfilesNotice.QUEUED
                            isNew -> ReleaseProfilesNotice.CREATED
                            else -> ReleaseProfilesNotice.UPDATED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, writeError = result.error)
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
            when (val result = repository.deleteReleaseProfile(id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        confirmDeleteId = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            ReleaseProfilesNotice.QUEUED
                        } else {
                            ReleaseProfilesNotice.DELETED
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
