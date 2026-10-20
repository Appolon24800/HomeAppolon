package dev.appolon.homelab.arr.feature.radarr.settings.mm.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.ColonReplacementFormat
import dev.appolon.homelab.arr.api.radarr.model.NamingConfigResource
import dev.appolon.homelab.arr.api.radarr.model.NamingExampleResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.mm.data.NamingPreviewRenderer
import dev.appolon.homelab.arr.feature.radarr.settings.mm.data.RadarrMediaManagementRepository
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
 * Editable naming draft. The two formats stay raw strings: the token chips
 * and the cursor insertion logic treat them as text, and the live preview
 * re-renders on every keystroke.
 */
data class NamingDraft(
    val renameMovies: Boolean = false,
    val replaceIllegalCharacters: Boolean = false,
    val colonReplacementFormat: ColonReplacementFormat = ColonReplacementFormat.SMART,
    val standardMovieFormat: String = "",
    val movieFolderFormat: String = "",
) {
    constructor(config: NamingConfigResource) : this(
        renameMovies = config.renameMovies,
        replaceIllegalCharacters = config.replaceIllegalCharacters,
        colonReplacementFormat = config.colonReplacementFormat ?: ColonReplacementFormat.SMART,
        standardMovieFormat = config.standardMovieFormat ?: "",
        movieFolderFormat = config.movieFolderFormat ?: "",
    )

    fun applyOnto(config: NamingConfigResource): NamingConfigResource = config.copy(
        renameMovies = renameMovies,
        replaceIllegalCharacters = replaceIllegalCharacters,
        colonReplacementFormat = colonReplacementFormat,
        standardMovieFormat = standardMovieFormat,
        movieFolderFormat = movieFolderFormat,
    )
}

/** Content payload of the naming editor's LCE state. */
data class RadarrNamingContent(
    val draft: NamingDraft,
    val dirty: Boolean,
    val saving: Boolean,
    val pendingActions: Int,
    /** Offline sample rendering of the current tokens, always present. */
    val localMoviePreview: String,
    val localFolderPreview: String,
    /** Server-rendered samples when the naming examples endpoint answered. */
    val serverExample: NamingExampleResource?,
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
class RadarrNamingViewModel(
    private val serverId: ServerId,
    private val repository: RadarrMediaManagementRepository,
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
        val serverExample: NamingExampleResource? = null,
        val notice: NamingNotice? = null,
        val writeError: ArrError? = null,
    )

    private var loadedConfig: NamingConfigResource = NamingConfigResource()
    private val revision = MutableStateFlow(0)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrNamingContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    RadarrNamingContent(
                        draft = s.draft,
                        dirty = s.dirty,
                        saving = s.saving,
                        pendingActions = s.pendingActions,
                        localMoviePreview = NamingPreviewRenderer.render(s.draft.standardMovieFormat),
                        localFolderPreview = NamingPreviewRenderer.render(s.draft.movieFolderFormat),
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

    /** Token chip insertion: returns the format with [token] spliced in at [cursor]. */
    fun insertToken(format: String, cursor: Int, token: String): Pair<String, Int> {
        val clamped = cursor.coerceIn(0, format.length)
        // A space glues the chip in when it lands right after a word or
        // another token; after `{` or a separator the splice is exact.
        val needsSpace = clamped > 0 && with(format[clamped - 1]) {
            isLetterOrDigit() || this == '}'
        }
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
                renameMovies = draft.renameMovies,
                replaceIllegalCharacters = draft.replaceIllegalCharacters,
                colonReplacementFormat = draft.colonReplacementFormat,
                standardMovieFormat = draft.standardMovieFormat,
                movieFolderFormat = draft.movieFolderFormat,
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
