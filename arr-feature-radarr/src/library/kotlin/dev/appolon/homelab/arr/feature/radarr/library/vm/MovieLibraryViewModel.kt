package dev.appolon.homelab.arr.feature.radarr.library.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.MovieEditorResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieCommand
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieLibraryFilters
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieLibrarySorts
import dev.appolon.homelab.arr.feature.radarr.library.data.MoviePresentation
import dev.appolon.homelab.arr.feature.radarr.library.data.RadarrMoviesRepository
import dev.appolon.homelab.arr.feature.radarr.library.data.SavedMovieFilter
import dev.appolon.homelab.arr.feature.radarr.library.data.SavedMovieFiltersStore
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.library.data.newSavedFilter
import dev.appolon.homelab.arr.feature.radarr.library.data.parseTagIds
import dev.appolon.homelab.arr.feature.radarr.library.data.presentMovies
import dev.appolon.homelab.arr.feature.radarr.library.data.toFilterState
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import java.time.Year
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which layout the library renders. */
enum class LibraryView { POSTERS, TABLE }

/** Transient one-line result of a write, surfaced as a snackbar. */
sealed interface LibraryMessage {
    data object EditSynced : LibraryMessage
    data object EditQueued : LibraryMessage
    data object DeleteSynced : LibraryMessage
    data object DeleteQueued : LibraryMessage
    data object CommandStarted : LibraryMessage
    data object CommandQueued : LibraryMessage
    data class WriteFailed(val error: ArrError) : LibraryMessage
}

/** Form state of the bulk movie editor sheet; null fields mean leave unchanged. */
data class MovieEditorForm(
    val monitored: Boolean? = null,
    val qualityProfileId: Int? = null,
    val minimumAvailability: MovieStatusType? = null,
    val rootFolderPath: String = "",
    val tagsText: String = "",
    val applyTags: ApplyTags = ApplyTags.ADD,
    val moveFiles: Boolean = false,
    val tagsError: Boolean = false,
)

/**
 * State of the movies library screen. Everything the composables render is
 * here; presentation (filter, sort, window) derives through
 * [MovieLibraryFilters] helpers so it stays testable.
 */
data class MovieLibraryUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val movies: List<MovieResource> = emptyList(),
    val qualityProfiles: List<QualityProfileResource> = emptyList(),
    val filterState: FilterState = FilterState(),
    val savedFilters: List<SavedMovieFilter> = emptyList(),
    val activeSavedFilterId: String? = null,
    val sortId: String = MovieLibrarySorts.TITLE,
    val sortDescending: Boolean = false,
    val view: LibraryView = LibraryView.POSTERS,
    val visibleCount: Int = MoviePresentation.INITIAL_COUNT,
    val selectMode: Boolean = false,
    val selection: Set<Int> = emptySet(),
    val queuedForServer: Int = 0,
    val queuedMovieIds: Set<Int> = emptySet(),
    val refreshing: Boolean = false,
    val editorForm: MovieEditorForm? = null,
    val confirmDelete: Boolean = false,
    val message: LibraryMessage? = null,
) {

    /** Filter options derived from the movies in view. */
    val options: MovieLibraryFilters.Options by lazy(LazyThreadSafetyMode.NONE) {
        MovieLibraryFilters.optionsFrom(movies, Year.now().value)
    }

    /** The filtered, sorted, windowed presentation the lazy lists render. */
    val presentation: MoviePresentation by lazy(LazyThreadSafetyMode.NONE) {
        presentMovies(movies, options, filterState, sortId, sortDescending, visibleCount)
    }

    /** Label of the quality profile, falling back to the raw id. */
    fun profileLabel(id: Int): String =
        qualityProfiles.firstOrNull { it.id == id }?.name ?: "#$id"

    /** ids the next bulk operation targets: the selection, or everything presented. */
    val bulkTargets: List<Int>
        get() = if (selectMode && selection.isNotEmpty()) selection.sorted() else presentation.all.map { it.id }
}

/**
 * ViewModel of the Radarr movies library. Owns no Android types so unit tests
 * construct it directly against fakes; the destination composable wires it to
 * the [ArrDataGraph] singletons.
 */
class MovieLibraryViewModel(
    private val repository: RadarrMoviesRepository,
    private val savedFilters: SavedMovieFiltersStore,
) : ViewModel() {

    private val _state = MutableStateFlow(MovieLibraryUiState())
    val state: StateFlow<MovieLibraryUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<MovieLibraryUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.movies.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.movies.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeMovies()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { movies -> _state.update { it.copy(movies = movies, loaded = true) } }
        }
        viewModelScope.launch {
            savedFilters.observe()
                .catch { }
                .collect { filters ->
                    _state.update { current ->
                        val stillThere = current.activeSavedFilterId
                            ?.let { id -> filters.any { it.id == id } } == true
                        current.copy(
                            savedFilters = filters,
                            activeSavedFilterId = current.activeSavedFilterId.takeIf { stillThere },
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.queuedCount()
                .catch { }
                .collect { count -> _state.update { it.copy(queuedForServer = count) } }
        }
        viewModelScope.launch {
            repository.queuedMovieIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedMovieIds = ids) } }
        }
        viewModelScope.launch { refreshProfiles() }
        refresh()
    }

    /** Pull-to-refresh and error retry entry point. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refresh()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err -> _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }

    fun setView(view: LibraryView) = _state.update { it.copy(view = view) }

    fun setSort(sortId: String) =
        _state.update { it.copy(sortId = sortId, visibleCount = MoviePresentation.INITIAL_COUNT) }

    fun toggleSortDescending() = _state.update { it.copy(sortDescending = !it.sortDescending) }

    /** Grows the visible window by one page; a no-op at the end of the list. */
    fun loadMore() = _state.update { s ->
        if (s.presentation.endReached) {
            s
        } else {
            s.copy(visibleCount = s.visibleCount + MoviePresentation.PAGE_COUNT)
        }
    }

    fun setFilterValue(id: String, value: FilterValue) = _state.update { s ->
        val next = if (value == emptyValue(id)) s.filterState.without(id) else s.filterState.with(id, value)
        s.copy(
            filterState = next,
            activeSavedFilterId = null,
            visibleCount = MoviePresentation.INITIAL_COUNT,
        )
    }

    fun clearFilters() = _state.update { s ->
        s.copy(filterState = FilterState(), activeSavedFilterId = null)
    }

    /** Saves the current filter state under a label and activates it. */
    fun saveFilter(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        val filter = newSavedFilter(trimmed, _state.value.filterState)
        viewModelScope.launch {
            savedFilters.save(filter)
            _state.update { it.copy(activeSavedFilterId = filter.id) }
        }
    }

    fun applySavedFilter(id: String) {
        val filter = _state.value.savedFilters.firstOrNull { it.id == id } ?: return
        _state.update { s ->
            if (s.activeSavedFilterId == id) {
                s.copy(filterState = FilterState(), activeSavedFilterId = null)
            } else {
                s.copy(
                    filterState = filter.toFilterState(),
                    activeSavedFilterId = id,
                    visibleCount = MoviePresentation.INITIAL_COUNT,
                )
            }
        }
    }

    fun deleteSavedFilter(id: String) {
        viewModelScope.launch { savedFilters.delete(id) }
    }

    fun enterSelectMode(first: Int) =
        _state.update { it.copy(selectMode = true, selection = setOf(first)) }

    fun exitSelectMode() = _state.update { it.copy(selectMode = false, selection = emptySet()) }

    fun toggleSelected(movieId: Int) = _state.update { s ->
        val next = if (movieId in s.selection) s.selection - movieId else s.selection + movieId
        s.copy(selection = next, selectMode = next.isNotEmpty() || s.selectMode)
    }

    fun selectAllPresented() = _state.update { s ->
        s.copy(selection = s.presentation.all.map { it.id }.toSet(), selectMode = true)
    }

    fun openEditor() = _state.update { it.copy(editorForm = MovieEditorForm()) }

    fun closeEditor() = _state.update { it.copy(editorForm = null) }

    fun updateEditor(transform: (MovieEditorForm) -> MovieEditorForm) =
        _state.update { s ->
            val form = s.editorForm ?: return@update s
            s.copy(editorForm = transform(form))
        }

    /** Applies the editor form to the current bulk targets. */
    fun submitEditor() {
        val form = _state.value.editorForm ?: return
        val tags = parseTagIds(form.tagsText)
        if (tags == null) {
            _state.update { it.copy(editorForm = form.copy(tagsError = true)) }
            return
        }
        val editor = MovieEditorResource(
            movieIds = _state.value.bulkTargets,
            monitored = form.monitored,
            qualityProfileId = form.qualityProfileId,
            minimumAvailability = form.minimumAvailability,
            rootFolderPath = form.rootFolderPath.trim().ifEmpty { null },
            tags = tags.takeIf { it.isNotEmpty() || form.applyTags == ApplyTags.REPLACE },
            applyTags = tags.takeIf { it.isNotEmpty() || form.applyTags == ApplyTags.REPLACE }
                ?.let { form.applyTags },
            moveFiles = form.moveFiles,
        )
        viewModelScope.launch {
            when (val outcome = repository.editMovies(editor)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        editorForm = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            LibraryMessage.EditQueued
                        } else {
                            LibraryMessage.EditSynced
                        },
                    )
                }
                is ArrResult.Err -> _state.update { s ->
                    s.copy(message = LibraryMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }

    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    /** Bulk-deletes the current targets with the chosen flags. */
    fun confirmDelete(deleteFiles: Boolean, addImportExclusion: Boolean) {
        val targets = _state.value.bulkTargets
        _state.update { it.copy(confirmDelete = false) }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            when (val outcome = repository.deleteMovies(targets, deleteFiles, addImportExclusion)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        selectMode = false,
                        selection = emptySet(),
                        message = if (outcome.value is WriteOutcome.Queued) {
                            LibraryMessage.DeleteQueued
                        } else {
                            LibraryMessage.DeleteSynced
                        },
                    )
                }
                is ArrResult.Err -> _state.update { s ->
                    s.copy(message = LibraryMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    /** Refresh metadata for the selection, or the whole library when nothing is selected. */
    fun refreshMetadata() = dispatchCommand(MovieCommand.REFRESH_METADATA)

    /** Rescan disk for the selection, or the whole library when nothing is selected. */
    fun scanDisk() = dispatchCommand(MovieCommand.SCAN_DISK)

    private fun dispatchCommand(command: MovieCommand) {
        val scoped = _state.value.takeIf { it.selectMode && it.selection.isNotEmpty() }
            ?.selection?.sorted()
        viewModelScope.launch {
            when (val outcome = repository.runCommand(command, scoped)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        message = if (outcome.value is WriteOutcome.Queued) {
                            LibraryMessage.CommandQueued
                        } else {
                            LibraryMessage.CommandStarted
                        },
                    )
                }
                is ArrResult.Err -> _state.update { s ->
                    s.copy(message = LibraryMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun refreshProfiles() {
        when (val result = repository.qualityProfiles()) {
            is ArrResult.Ok -> _state.update { it.copy(qualityProfiles = result.value) }
            is ArrResult.Err -> Unit
        }
    }

    private fun emptyValue(id: String): FilterValue = when (id) {
        MovieLibraryFilters.YEAR -> FilterValue.Range(null, null)
        else -> FilterValue.Choice(emptySet())
    }
}
