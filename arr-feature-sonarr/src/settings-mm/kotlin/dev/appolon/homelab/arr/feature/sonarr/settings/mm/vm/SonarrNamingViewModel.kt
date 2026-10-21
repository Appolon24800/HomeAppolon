package dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.NamingConfigResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.NamingPreviewRenderer
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.SonarrMediaManagementRepository
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.SonarrNamingExamples
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editable naming draft. The six formats stay raw strings: the token
 * chips and the cursor insertion logic treat them as text, and the live
 * preview re-renders on every keystroke. Colon replacement and
 * multi-episode style keep their raw integer wire values.
 */
data class NamingDraft(
    val renameEpisodes: Boolean = false,
    val replaceIllegalCharacters: Boolean = false,
    val colonReplacementFormat: Int = COLON_SMART,
    val customColonReplacementFormat: String = "",
    val multiEpisodeStyle: Int = MULTI_PREFIXED_RANGE,
    val standardEpisodeFormat: String = "",
    val dailyEpisodeFormat: String = "",
    val animeEpisodeFormat: String = "",
    val seriesFolderFormat: String = "",
    val seasonFolderFormat: String = "",
    val specialsFolderFormat: String = "",
) {
    constructor(config: NamingConfigResource) : this(
        renameEpisodes = config.renameEpisodes,
        replaceIllegalCharacters = config.replaceIllegalCharacters,
        colonReplacementFormat = config.colonReplacementFormat,
        customColonReplacementFormat = config.customColonReplacementFormat ?: "",
        multiEpisodeStyle = config.multiEpisodeStyle,
        standardEpisodeFormat = config.standardEpisodeFormat ?: "",
        dailyEpisodeFormat = config.dailyEpisodeFormat ?: "",
        animeEpisodeFormat = config.animeEpisodeFormat ?: "",
        seriesFolderFormat = config.seriesFolderFormat ?: "",
        seasonFolderFormat = config.seasonFolderFormat ?: "",
        specialsFolderFormat = config.specialsFolderFormat ?: "",
    )

    fun applyOnto(config: NamingConfigResource): NamingConfigResource = config.copy(
        renameEpisodes = renameEpisodes,
        replaceIllegalCharacters = replaceIllegalCharacters,
        colonReplacementFormat = colonReplacementFormat,
        customColonReplacementFormat = customColonReplacementFormat.takeUnless { it.isBlank() },
        multiEpisodeStyle = multiEpisodeStyle,
        standardEpisodeFormat = standardEpisodeFormat,
        dailyEpisodeFormat = dailyEpisodeFormat,
        animeEpisodeFormat = animeEpisodeFormat,
        seriesFolderFormat = seriesFolderFormat,
        seasonFolderFormat = seasonFolderFormat,
        specialsFolderFormat = specialsFolderFormat,
    )

    companion object {
        /** Sonarr ColonReplacementFormat wire values (Delete..Custom, 0-5). */
        const val COLON_DELETE = 0
        const val COLON_DASH = 1
        const val COLON_SPACE_DASH = 2
        const val COLON_SPACE_DASH_SPACE = 3
        const val COLON_SMART = 4
        const val COLON_CUSTOM = 5

        /** Sonarr MultiEpisodeStyle wire values (Extend..PrefixedRange, 0-5). */
        const val MULTI_EXTEND = 0
        const val MULTI_DUPLICATE = 1
        const val MULTI_REPEAT = 2
        const val MULTI_SCENE = 3
        const val MULTI_RANGE = 4
        const val MULTI_PREFIXED_RANGE = 5
    }
}

/** Content payload of the naming editor's LCE state. */
data class SonarrNamingContent(
    val draft: NamingDraft,
    val dirty: Boolean,
    val saving: Boolean,
    val pendingActions: Int,
    /** Offline sample rendering of the current tokens, always present. */
    val localStandardPreview: String,
    val localDailyPreview: String,
    val localAnimePreview: String,
    val localSeriesFolderPreview: String,
    val localSeasonFolderPreview: String,
    val localSpecialsFolderPreview: String,
    /** Server-rendered samples when the naming examples endpoint answered. */
    val serverExample: SonarrNamingExamples?,
)

/** Transient results of saving, mapped to strings by the screen. */
enum class NamingNotice {
    SAVED,
    SAVED_QUEUED,
}

/**
 * Naming config editor state. The live preview is local and instant
 * ([NamingPreviewRenderer] substitutes samples for the current tokens on
 * every edit), and a debounced call to the naming examples endpoint
 * replaces it with the server's authoritative sample when online. A
 * failed example fetch never blocks editing: the local preview stays.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SonarrNamingViewModel(
    private val serverId: ServerId,
    private val repository: SonarrMediaManagementRepository,
    /** Debounce for the server example fetch, injectable for virtual-time tests. */
    private val exampleDebounceMillis: Long = 400,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val draft: NamingDraft = NamingDraft(),
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val pendingActions: Int = 0,
        val serverExample: SonarrNamingExamples? = null,
        val notice: NamingNotice? = null,
        val writeError: ArrError? = null,
    )

    private var loadedConfig: NamingConfigResource = NamingConfigResource()
    private val revision = MutableStateFlow(0)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SonarrNamingContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    SonarrNamingContent(
                        draft = s.draft,
                        dirty = s.dirty,
                        saving = s.saving,
                        pendingActions = s.pendingActions,
                        localStandardPreview = NamingPreviewRenderer.render(s.draft.standardEpisodeFormat),
                        localDailyPreview = NamingPreviewRenderer.render(s.draft.dailyEpisodeFormat),
                        localAnimePreview = NamingPreviewRenderer.render(s.draft.animeEpisodeFormat),
                        localSeriesFolderPreview = NamingPreviewRenderer.render(s.draft.seriesFolderFormat),
                        localSeasonFolderPreview = NamingPreviewRenderer.render(s.draft.seasonFolderFormat),
                        localSpecialsFolderPreview = NamingPreviewRenderer.render(s.draft.specialsFolderFormat),
                        serverExample = s.serverExample,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeNaming().collect { config ->
                if (config != null) {
                    loadedConfig = config
                    _state.update { s ->
                        if (s.dirty) {
                            s.copy(loaded = true, loadError = null)
                        } else {
                            s.copy(loaded = true, loadError = null, draft = NamingDraft(config))
                        }
                    }
                    revision.value += 1
                }
            }
        }
        viewModelScope.launch {
            repository.queuedCount().collect { pending ->
                _state.update { it.copy(pendingActions = pending) }
            }
        }
        // The draft drives the debounced server example fetch; the revision
        // counter also ticks after a background load so the first example
        // arrives without an edit.
        viewModelScope.launch {
            revision
                .debounce(exampleDebounceMillis)
                .distinctUntilChanged()
                .collect { fetchServerExample() }
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

    fun edit(transform: (NamingDraft) -> NamingDraft) {
        _state.update { it.copy(draft = transform(it.draft), dirty = true, serverExample = null) }
        revision.value += 1
    }

    /**
     * Token chip insertion: returns the format with [token] spliced in at
     * [cursor]. A separating space is glued on only when appending after a
     * non-separator character at the very end; mid-string insertions land
     * exactly where the cursor sits. (The committed Radarr variant also
     * skips the trailing space because its token check inverts, which its
     * own test disputes; this copy follows the tested behavior.)
     */
    fun insertToken(format: String, cursor: Int, token: String): Pair<String, Int> {
        val clamped = cursor.coerceIn(0, format.length)
        val needsSpace = clamped == format.length && clamped > 0 &&
            !format[clamped - 1].isWhitespace() && format[clamped - 1] != '{'
        val prefix = format.take(clamped)
        val suffix = format.drop(clamped)
        val glue = if (needsSpace) " " else ""
        val joined = prefix + glue + token
        return joined + suffix to joined.length
    }

    fun save() {
        val current = _state.value
        if (!current.dirty || current.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.saveNaming(current.draft.applyOnto(loadedConfig))) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            NamingNotice.SAVED_QUEUED
                        } else {
                            NamingNotice.SAVED
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

    private suspend fun fetchServerExample() {
        val draft = _state.value.draft
        when (
            val result = repository.namingExamples(
                renameEpisodes = draft.renameEpisodes,
                replaceIllegalCharacters = draft.replaceIllegalCharacters,
                colonReplacementFormat = draft.colonReplacementFormat,
                customColonReplacementFormat = draft.customColonReplacementFormat.ifBlank { null },
                multiEpisodeStyle = draft.multiEpisodeStyle,
                standardEpisodeFormat = draft.standardEpisodeFormat,
                dailyEpisodeFormat = draft.dailyEpisodeFormat,
                animeEpisodeFormat = draft.animeEpisodeFormat,
                seriesFolderFormat = draft.seriesFolderFormat,
                seasonFolderFormat = draft.seasonFolderFormat,
                specialsFolderFormat = draft.specialsFolderFormat,
            )
        ) {
            is ArrResult.Ok -> _state.update { s ->
                // A newer edit may have landed while fetching; only the
                // matching revision's example is kept.
                if (s.dirty && s.draft != draft) s else s.copy(serverExample = result.value)
            }

            is ArrResult.Err -> Unit
        }
    }
}
