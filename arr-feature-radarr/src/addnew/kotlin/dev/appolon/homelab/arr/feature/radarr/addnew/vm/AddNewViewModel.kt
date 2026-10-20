package dev.appolon.homelab.arr.feature.radarr.addnew.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.radarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.radarr.addnew.data.AddNewLogic
import dev.appolon.homelab.arr.feature.radarr.addnew.data.AddOutcome
import dev.appolon.homelab.arr.feature.radarr.addnew.data.RadarrAddNewRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Content tabs of the Add new screen. */
enum class AddNewTab { DISCOVER, COLLECTIONS }

/** Transient one-line result of a write, surfaced as a snackbar. */
sealed interface AddNewMessage {
    data class Added(val title: String) : AddNewMessage
    data class AddedAndSearching(val title: String) : AddNewMessage
    data class AddQueued(val title: String) : AddNewMessage
    data class AddFailed(val title: String) : AddNewMessage
}

/** Form state of the add sheet opened for one search or discovery result. */
data class AddSheetUiState(
    val candidate: MovieResource,
    val rootFolderPath: String? = null,
    val qualityProfileId: Int? = null,
    val monitor: MonitorTypes = MonitorTypes.MOVIE_ONLY,
    val minimumAvailability: MovieStatusType = MovieStatusType.RELEASED,
    val selectedTags: Set<Int> = emptySet(),
    val submitting: Boolean = false,
) {
    /** Adds stay disabled until both a root folder and a profile are chosen. */
    val submittable: Boolean get() = rootFolderPath != null && qualityProfileId != null
}

/**
 * State of the Add new screen: search with year disambiguation, the discover
 * feed, the add-option catalogs, and the add sheet. Everything the
 * composables render lives here.
 */
data class AddNewUiState(
    val tab: AddNewTab = AddNewTab.DISCOVER,
    val query: String = "",
    val parsedYear: Int? = null,
    val searched: Boolean = false,
    val searching: Boolean = false,
    val searchResults: List<MovieResource> = emptyList(),
    val searchError: ArrError? = null,
    val existingTmdbIds: Set<Int> = emptySet(),
    val queuedTmdbIds: Set<Int> = emptySet(),
    val rootFolders: List<RootFolderResource> = emptyList(),
    val qualityProfiles: List<QualityProfileResource> = emptyList(),
    val tags: List<TagResource> = emptyList(),
    val defaultsError: ArrError? = null,
    val discovery: List<ImportListMoviesResource> = emptyList(),
    val discoveryLoaded: Boolean = false,
    val discoveryError: ArrError? = null,
    val discoveryRefreshing: Boolean = false,
    val addSheet: AddSheetUiState? = null,
    val message: AddNewMessage? = null,
    val pendingCount: Int = 0,
) {
    /** True while a query is typed, switching the content area to results. */
    val searchActive: Boolean get() = query.isNotBlank()

    /** LCE for the results area, or null before the first search (idle hint). */
    val searchLce: LceState<List<MovieResource>>?
        get() = when {
            !searched && !searching -> null
            searching -> LceState.Loading
            searchError != null -> LceState.Error(searchError)
            searchResults.isEmpty() -> LceState.Empty
            else -> LceState.Content(searchResults)
        }

    /** LCE for the discover tab. */
    val discoveryLce: LceState<List<ImportListMoviesResource>>
        get() = when {
            !discoveryLoaded && discoveryError == null -> LceState.Loading
            discoveryError != null && discovery.isEmpty() -> LceState.Error(discoveryError)
            discovery.isEmpty() -> LceState.Empty
            else -> LceState.Content(discovery, refreshing = discoveryRefreshing)
        }
}

/**
 * ViewModel of the Add new screen: search, discovery, and the add sheet.
 * Owns no Android types so unit tests construct it directly against fakes.
 */
class AddNewViewModel(
    private val repository: RadarrAddNewRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddNewUiState())
    val state: StateFlow<AddNewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLibraryMovies()
                .catch { }
                .collect { movies ->
                    _state.update {
                        it.copy(existingTmdbIds = movies.map { m -> m.tmdbId }.toSet())
                    }
                }
        }
        viewModelScope.launch {
            repository.queuedTmdbIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedTmdbIds = ids) } }
        }
        viewModelScope.launch {
            repository.queuedCount()
                .catch { }
                .collect { count -> _state.update { it.copy(pendingCount = count) } }
        }
        viewModelScope.launch { repository.syncLibraryIfStale() }
        viewModelScope.launch { loadDiscovery() }
        loadCatalogs()
    }

    fun setTab(tab: AddNewTab) = _state.update { it.copy(tab = tab) }

    /** Stores the raw query and its parsed year; a blank query clears results. */
    fun onQueryChange(value: String) {
        val parsed = AddNewLogic.parseSearchQuery(value)
        _state.update {
            if (value.isBlank()) {
                it.copy(
                    query = value,
                    parsedYear = null,
                    searched = false,
                    searching = false,
                    searchResults = emptyList(),
                    searchError = null,
                )
            } else {
                it.copy(query = value, parsedYear = parsed.year)
            }
        }
    }

    /** Runs the lookup with year disambiguation and year-first ranking. */
    fun submitSearch() {
        val query = _state.value.query
        if (query.isBlank() || _state.value.searching) return
        _state.update { it.copy(searching = true, searchError = null) }
        viewModelScope.launch {
            when (val result = repository.search(query)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(searching = false, searched = true, searchResults = result.value)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        searching = false,
                        searched = true,
                        searchResults = emptyList(),
                        searchError = result.error,
                    )
                }
            }
        }
    }

    /** Opens the add sheet for one result, preselecting the catalog defaults. */
    fun openAddSheet(candidate: MovieResource) {
        val current = _state.value
        _state.update {
            it.copy(
                addSheet = AddSheetUiState(
                    candidate = candidate,
                    rootFolderPath = current.rootFolders.firstOrNull()?.path,
                    qualityProfileId = current.qualityProfiles.firstOrNull()?.id,
                ),
            )
        }
    }

    fun updateAddSheet(transform: (AddSheetUiState) -> AddSheetUiState) {
        _state.update { state ->
            state.addSheet?.let { sheet -> state.copy(addSheet = transform(sheet)) } ?: state
        }
    }

    fun dismissAddSheet() = _state.update { it.copy(addSheet = null) }

    /** Adds the sheet's movie, optionally starting an immediate search. */
    fun submitAdd(searchForMovie: Boolean) {
        val sheet = _state.value.addSheet ?: return
        if (!sheet.submittable || sheet.submitting) return
        updateAddSheet { it.copy(submitting = true) }
        viewModelScope.launch {
            val payload = AddNewLogic.toAddMoviePayload(
                candidate = sheet.candidate,
                rootFolderPath = sheet.rootFolderPath.orEmpty(),
                qualityProfileId = sheet.qualityProfileId ?: 0,
                monitor = sheet.monitor,
                minimumAvailability = sheet.minimumAvailability,
                tags = sheet.selectedTags,
                searchForMovie = searchForMovie,
            )
            val title = sheet.candidate.title.orEmpty()
                .ifEmpty { sheet.candidate.tmdbId.toString() }
            when (val result = repository.addMovie(payload)) {
                is ArrResult.Ok -> {
                    val message = when (result.value) {
                        AddOutcome.Synced ->
                            if (searchForMovie) {
                                AddNewMessage.AddedAndSearching(title)
                            } else {
                                AddNewMessage.Added(title)
                            }

                        is AddOutcome.Queued -> AddNewMessage.AddQueued(title)
                    }
                    _state.update { it.copy(addSheet = null, message = message) }
                }

                is ArrResult.Err -> _state.update {
                    it.copy(message = AddNewMessage.AddFailed(title))
                }
            }
        }
    }

    /** Pull-to-refresh and re-entry: catalogs, library, and discovery. */
    fun refreshAll() {
        _state.update { it.copy(discoveryRefreshing = true) }
        viewModelScope.launch {
            repository.syncLibraryIfStale(ttlMillis = 0)
            loadDiscovery(ttlMillis = 0)
        }
        loadCatalogs()
    }

    fun refreshDiscovery() {
        _state.update { it.copy(discoveryRefreshing = true) }
        viewModelScope.launch { loadDiscovery(ttlMillis = 0) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun loadDiscovery(
        ttlMillis: Long = RadarrAddNewRepository.DISCOVERY_TTL_MILLIS,
    ) {
        when (val result = repository.discover(ttlMillis)) {
            is ArrResult.Ok -> _state.update {
                it.copy(
                    discovery = result.value,
                    discoveryLoaded = true,
                    discoveryError = null,
                    discoveryRefreshing = false,
                )
            }

            is ArrResult.Err -> _state.update {
                it.copy(
                    discoveryLoaded = it.discovery.isNotEmpty(),
                    discoveryError = result.error,
                    discoveryRefreshing = false,
                )
            }
        }
    }

    private fun loadCatalogs() {
        viewModelScope.launch {
            when (val result = repository.rootFolders()) {
                is ArrResult.Ok -> _state.update { it.copy(rootFolders = result.value) }
                is ArrResult.Err -> _state.update { it.copy(defaultsError = result.error) }
            }
        }
        viewModelScope.launch {
            when (val result = repository.qualityProfiles()) {
                is ArrResult.Ok -> _state.update { it.copy(qualityProfiles = result.value) }
                is ArrResult.Err -> _state.update { it.copy(defaultsError = result.error) }
            }
        }
        viewModelScope.launch {
            when (val result = repository.tags()) {
                is ArrResult.Ok -> _state.update { it.copy(tags = result.value) }
                is ArrResult.Err -> _state.update { it.copy(defaultsError = result.error) }
            }
        }
    }
}
