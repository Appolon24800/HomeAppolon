package dev.appolon.homelab.arr.feature.bazarr.series.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrBadges
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrBlacklistEntry
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLanguageProfile
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLibraryFilters
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLibraryRepository
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMediaKind
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMovie
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMovieSorts
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrPresentation
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrSeries
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrSeriesSorts
import dev.appolon.homelab.arr.feature.bazarr.series.data.WriteOutcome
import dev.appolon.homelab.arr.feature.bazarr.series.data.presentMovies
import dev.appolon.homelab.arr.feature.bazarr.series.data.presentSeries
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which layout the lists render. */
enum class LibraryView { POSTERS, TABLE }

/** Transient one-line result of a write, surfaced as a snackbar. */
sealed interface LibraryMessage {
    data object ProfileSynced : LibraryMessage
    data object ProfileQueued : LibraryMessage
    data class WriteFailed(val error: ArrError) : LibraryMessage
}

/**
 * State of one Bazarr list screen. The repository serves both kinds, but a
 * screen instance only loads and renders its own; the other list stays empty
 * and its derived presentation is never read. Everything the composables
 * render is here; presentation (filter, sort, window) derives through the
 * pure helpers so it stays testable.
 */
data class BazarrLibraryUiState(
    val kind: BazarrMediaKind = BazarrMediaKind.SERIES,
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val series: List<BazarrSeries> = emptyList(),
    val movies: List<BazarrMovie> = emptyList(),
    val profiles: List<BazarrLanguageProfile> = emptyList(),
    val seriesBlacklist: List<BazarrBlacklistEntry> = emptyList(),
    val movieBlacklist: List<BazarrBlacklistEntry> = emptyList(),
    val badges: BazarrBadges? = null,
    val filterState: FilterState = FilterState(),
    val sortId: String = BazarrSeriesSorts.TITLE,
    val sortDescending: Boolean = false,
    val view: LibraryView = LibraryView.POSTERS,
    val visibleCount: Int = BazarrPresentation.INITIAL_COUNT,
    val selectMode: Boolean = false,
    val selection: Set<Int> = emptySet(),
    val queuedForServer: Int = 0,
    val queuedIds: Set<Int> = emptySet(),
    val refreshing: Boolean = false,
    val message: LibraryMessage? = null,
) {

    /** Filter options derived from the active kind's data and joins. */
    val options: BazarrLibraryFilters.Options by lazy(LazyThreadSafetyMode.NONE) {
        when (kind) {
            BazarrMediaKind.SERIES -> BazarrLibraryFilters.seriesOptions(
                series, seriesBlacklist, profiles,
            )
            BazarrMediaKind.MOVIES -> BazarrLibraryFilters.movieOptions(
                movies, movieBlacklist, profiles,
            )
        }
    }

    /** The filtered, sorted, windowed presentation of the series list. */
    val seriesPresentation: BazarrPresentation<BazarrSeries> by lazy(LazyThreadSafetyMode.NONE) {
        presentSeries(series, options, filterState, sortId, sortDescending, visibleCount)
    }

    /** The filtered, sorted, windowed presentation of the movies list. */
    val moviePresentation: BazarrPresentation<BazarrMovie> by lazy(LazyThreadSafetyMode.NONE) {
        presentMovies(movies, options, filterState, sortId, sortDescending, visibleCount)
    }

    /** True when the windowed presentation reaches the end of the active list. */
    val endReached: Boolean
        get() = if (kind == BazarrMediaKind.SERIES) {
            seriesPresentation.endReached
        } else {
            moviePresentation.endReached
        }

    /** Filter specs of the active kind, the single source for chips and sheet. */
    val filterSpecs: List<dev.appolon.homelab.arr.core.FilterSpec> by lazy(LazyThreadSafetyMode.NONE) {
        when (kind) {
            BazarrMediaKind.SERIES -> BazarrLibraryFilters.seriesSpecs(options)
            BazarrMediaKind.MOVIES -> BazarrLibraryFilters.movieSpecs(options)
        }
    }

    /** Sort keys of the active kind. */
    val sortOptions: List<String> =
        if (kind == BazarrMediaKind.SERIES) BazarrSeriesSorts.ALL else BazarrMovieSorts.ALL

    /** Label of a language profile, falling back to the raw id. */
    fun profileLabel(id: Int?): String =
        id?.let { profiles.firstOrNull { p -> p.profileId == it }?.label } ?: "#$id"

    /** ids the next bulk operation targets: the selection, or everything presented. */
    val bulkTargets: List<Int>
        get() = if (selectMode && selection.isNotEmpty()) {
            selection.sorted()
        } else {
            when (kind) {
                BazarrMediaKind.SERIES ->
                    seriesPresentation.all.map { it.seriesId }
                BazarrMediaKind.MOVIES ->
                    moviePresentation.all.map { it.radarrId }
            }
        }
}

/**
 * ViewModel of the Bazarr series and movies lists. Owns no Android types so
 * unit tests construct it directly against fakes; the destination composable
 * wires it to the [dev.appolon.homelab.arr.data.ArrDataGraph] singletons.
 */
class BazarrLibraryViewModel(
    private val repository: BazarrLibraryRepository,
    kind: BazarrMediaKind,
) : ViewModel() {

    private val _state = MutableStateFlow(BazarrLibraryUiState(kind = kind))
    val state: StateFlow<BazarrLibraryUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<BazarrLibraryUiState>> = state
        .map { s ->
            val empty = when (s.kind) {
                BazarrMediaKind.SERIES -> s.series.isEmpty()
                BazarrMediaKind.MOVIES -> s.movies.isEmpty()
            }
            when {
                s.loadError != null && empty -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                empty -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        when (kind) {
            BazarrMediaKind.SERIES -> {
                viewModelScope.launch {
                    repository.observeSeries()
                        .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                        .collect { series ->
                            _state.update { it.copy(series = series, loaded = true) }
                        }
                }
                viewModelScope.launch {
                    repository.observeEpisodeBlacklist()
                        .catch { }
                        .collect { entries ->
                            _state.update { it.copy(seriesBlacklist = entries) }
                        }
                }
                viewModelScope.launch {
                    repository.queuedSeriesIds()
                        .catch { }
                        .collect { ids -> _state.update { it.copy(queuedIds = ids) } }
                }
            }

            BazarrMediaKind.MOVIES -> {
                viewModelScope.launch {
                    repository.observeMovies()
                        .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                        .collect { movies ->
                            _state.update { it.copy(movies = movies, loaded = true) }
                        }
                }
                viewModelScope.launch {
                    repository.observeMovieBlacklist()
                        .catch { }
                        .collect { entries -> _state.update { it.copy(movieBlacklist = entries) } }
                }
                viewModelScope.launch {
                    repository.queuedMovieIds()
                        .catch { }
                        .collect { ids -> _state.update { it.copy(queuedIds = ids) } }
                }
            }
        }
        viewModelScope.launch {
            repository.observeProfiles()
                .catch { }
                .collect { profiles -> _state.update { it.copy(profiles = profiles) } }
        }
        viewModelScope.launch {
            repository.observeBadges()
                .catch { }
                .collect { badges -> _state.update { it.copy(badges = badges) } }
        }
        viewModelScope.launch {
            repository.queuedCount()
                .catch { }
                .collect { count -> _state.update { it.copy(queuedForServer = count) } }
        }
        refresh()
    }

    /** Pull-to-refresh and error retry entry point. */
    fun refresh() {
        val kind = _state.value.kind
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val listResult = repository.refresh(kind)
            val supportResult = repository.refreshSupport()
            _state.update { s ->
                val error = when {
                    listResult is ArrResult.Err -> listResult.error
                    supportResult is ArrResult.Err -> supportResult.error
                    else -> null
                }
                s.copy(refreshing = false, loadError = error)
            }
        }
    }

    fun setView(view: LibraryView) = _state.update { it.copy(view = view) }

    fun setSort(sortId: String) =
        _state.update { it.copy(sortId = sortId, visibleCount = BazarrPresentation.INITIAL_COUNT) }

    fun toggleSortDescending() = _state.update { it.copy(sortDescending = !it.sortDescending) }

    /** Grows the visible window by one page; a no-op at the end of the list. */
    fun loadMore() = _state.update { s ->
        if (s.endReached) s else {
            s.copy(visibleCount = s.visibleCount + BazarrPresentation.PAGE_COUNT)
        }
    }

    fun setFilterValue(id: String, value: FilterValue) {
        val empty = BazarrLibraryFilters.emptyValue(id)
        _state.update { s ->
            val next = if (value == empty) s.filterState.without(id) else s.filterState.with(id, value)
            s.copy(filterState = next, visibleCount = BazarrPresentation.INITIAL_COUNT)
        }
    }

    fun clearFilters() =
        _state.update { it.copy(filterState = FilterState()) }

    fun enterSelectMode(first: Int) =
        _state.update { it.copy(selectMode = true, selection = setOf(first)) }

    fun exitSelectMode() = _state.update { it.copy(selectMode = false, selection = emptySet()) }

    fun toggleSelected(id: Int) = _state.update { s ->
        val next = if (id in s.selection) s.selection - id else s.selection + id
        s.copy(selection = next, selectMode = next.isNotEmpty() || s.selectMode)
    }

    fun selectAllPresented() = _state.update { s ->
        s.copy(selection = s.bulkTargets.toSet(), selectMode = true)
    }

    /**
     * Mass editor write: assigns one language profile to the bulk targets.
     * Offline assignments land optimistically and surface the queued
     * indicator through the collected [BazarrLibraryUiState.queuedIds].
     */
    fun applyProfile(profileId: Int) {
        val targets = _state.value.bulkTargets
        if (targets.isEmpty()) return
        val kind = _state.value.kind
        viewModelScope.launch {
            when (val outcome = repository.applyProfile(kind, targets, profileId)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        selectMode = false,
                        selection = emptySet(),
                        message = if (outcome.value is WriteOutcome.Queued) {
                            LibraryMessage.ProfileQueued
                        } else {
                            LibraryMessage.ProfileSynced
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
}
