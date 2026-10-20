package dev.appolon.homelab.arr.feature.lidarr.addnew.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.lidarr.addnew.data.AddNewLogic
import dev.appolon.homelab.arr.feature.lidarr.addnew.data.AddOutcome
import dev.appolon.homelab.arr.feature.lidarr.addnew.data.LidarrAddNewRepository
import dev.appolon.homelab.arr.feature.lidarr.addnew.data.SearchResults
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Content tabs of the Add new screen. */
enum class AddNewTab { STUDIO, LISTS }

/** Which catalog the search field queries. */
enum class SearchScope { ARTIST, ALBUM }

/** Transient one-line result of a write, surfaced as a snackbar. */
sealed interface AddNewMessage {
    data class ArtistAdded(val title: String) : AddNewMessage
    data class ArtistAddedAndSearching(val title: String) : AddNewMessage
    data class ArtistAddQueued(val title: String) : AddNewMessage
    data class ArtistAddFailed(val title: String) : AddNewMessage
    data class AlbumAdded(val title: String) : AddNewMessage
    data class AlbumAddedAndSearching(val title: String) : AddNewMessage
    data class AlbumAddQueued(val title: String) : AddNewMessage
    data class AlbumAddFailed(val title: String) : AddNewMessage
    data class StudioApplied(val artist: String, val count: Int) : AddNewMessage
    data class StudioQueued(val artist: String) : AddNewMessage
    data class StudioFailed(val artist: String) : AddNewMessage
}

/** Form state of the add sheet opened for one artist search result. */
data class ArtistAddSheetUiState(
    val candidate: ArtistResource,
    val rootFolderPath: String? = null,
    val qualityProfileId: Int? = null,
    val metadataProfileId: Int? = null,
    val monitor: MonitorTypes = MonitorTypes.ALL,
    val selectedTags: Set<Int> = emptySet(),
    val submitting: Boolean = false,
) {
    /** Adds stay disabled until folder and both profiles are chosen. */
    val submittable: Boolean
        get() = rootFolderPath != null && qualityProfileId != null && metadataProfileId != null
}

/**
 * Form state of the add sheet opened for one album search result. Standalone
 * albums file under a library artist, so the sheet resolves the artist from
 * the library first; without a match the only action is adding the artist.
 */
data class AlbumAddSheetUiState(
    val candidate: AlbumResource,
    val libraryArtist: ArtistResource? = null,
    val monitored: Boolean = true,
    val submitting: Boolean = false,
) {
    private val artistId: Int get() = libraryArtist?.id ?: 0
    val submittable: Boolean get() = artistId != 0 && !submitting
}

/** Discography picker state of one library artist. */
data class StudioPickerUiState(
    val artist: ArtistResource,
    val albums: List<AlbumResource> = emptyList(),
    val selection: Map<Int, Boolean> = emptyMap(),
    val strategy: MonitorTypes = MonitorTypes.NONE,
    val loading: Boolean = true,
    val error: ArrError? = null,
    val applying: Boolean = false,
) {
    /** Target monitored state per album id; unselected entries keep current. */
    fun targetOf(albumId: Int, current: Boolean): Boolean = selection[albumId] ?: current

    /** Albums whose target differs from the server state; what Apply sends. */
    val changedCount: Int
        get() = albums.count { targetOf(it.id, it.monitored) != it.monitored }
}

/**
 * State of the Add new screen: artist and album search with disambiguation
 * and badges, the studio discography picker, the import list browse, the
 * add-option catalogs, and the add sheets. Everything the composables render
 * lives here.
 */
data class AddNewUiState(
    val tab: AddNewTab = AddNewTab.STUDIO,
    val query: String = "",
    val scope: SearchScope = SearchScope.ARTIST,
    val searched: Boolean = false,
    val searching: Boolean = false,
    val searchResults: SearchResults = SearchResults(),
    val searchError: ArrError? = null,
    val existingForeignArtistIds: Set<String> = emptySet(),
    val queuedForeignArtistIds: Set<String> = emptySet(),
    val existingForeignAlbumIds: Set<String> = emptySet(),
    val queuedForeignAlbumIds: Set<String> = emptySet(),
    val libraryArtists: List<ArtistResource> = emptyList(),
    val rootFolders: List<RootFolderResource> = emptyList(),
    val qualityProfiles: List<QualityProfileResource> = emptyList(),
    val metadataProfiles: List<MetadataProfileResource> = emptyList(),
    val tags: List<TagResource> = emptyList(),
    val catalogsError: ArrError? = null,
    val lists: List<ImportListResource> = emptyList(),
    val listsLoaded: Boolean = false,
    val listsError: ArrError? = null,
    val listsRefreshing: Boolean = false,
    val artistSheet: ArtistAddSheetUiState? = null,
    val albumSheet: AlbumAddSheetUiState? = null,
    val studioPicker: StudioPickerUiState? = null,
    val message: AddNewMessage? = null,
    val pendingCount: Int = 0,
) {
    /** True while a query is typed, switching the content area to results. */
    val searchActive: Boolean get() = query.isNotBlank()

    /** LCE for the results area, or null before the first search (idle hint). */
    val searchLce: LceState<SearchResults>?
        get() = when {
            !searched && !searching -> null
            searching -> LceState.Loading
            searchError != null -> LceState.Error(searchError)
            searchResults.isEmpty -> LceState.Empty
            else -> LceState.Content(searchResults)
        }

    /** LCE for the studio tab's artist list. */
    val studioLce: LceState<List<ArtistResource>>
        get() = when {
            libraryArtists.isEmpty() && catalogsError != null -> LceState.Error(catalogsError)
            libraryArtists.isEmpty() && !listsLoaded && listsError == null -> LceState.Loading
            libraryArtists.isEmpty() -> LceState.Empty
            else -> LceState.Content(libraryArtists)
        }

    /** LCE for the import lists tab. */
    val listsLce: LceState<List<ImportListResource>>
        get() = when {
            !listsLoaded && listsError == null -> LceState.Loading
            listsError != null && lists.isEmpty() -> LceState.Error(listsError)
            lists.isEmpty() -> LceState.Empty
            else -> LceState.Content(lists, refreshing = listsRefreshing)
        }
}

/**
 * ViewModel of the Add new screen: search, studio picker, import lists, and
 * the add sheets. Owns no Android types so unit tests construct it directly
 * against fakes.
 */
class AddNewViewModel(
    private val repository: LidarrAddNewRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddNewUiState())
    val state: StateFlow<AddNewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLibraryArtists()
                .catch { }
                .collect { artists ->
                    _state.update { current ->
                        val existing = AddNewLogic.existingForeignArtistIds(artists)
                        val albumSheet = current.albumSheet?.let { sheet ->
                            sheet.copy(libraryArtist = matchArtist(artists, sheet.candidate))
                        }
                        current.copy(
                            libraryArtists = artists.sortedBy { it.artistName.orEmpty().lowercase() },
                            existingForeignArtistIds = existing,
                            albumSheet = albumSheet,
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.observeLibraryAlbums()
                .catch { }
                .collect { albums ->
                    _state.update {
                        it.copy(existingForeignAlbumIds = AddNewLogic.existingForeignAlbumIds(albums))
                    }
                }
        }
        viewModelScope.launch {
            repository.queuedForeignArtistIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedForeignArtistIds = ids) } }
        }
        viewModelScope.launch {
            repository.queuedForeignAlbumIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedForeignAlbumIds = ids) } }
        }
        viewModelScope.launch {
            repository.queuedCount()
                .catch { }
                .collect { count -> _state.update { it.copy(pendingCount = count) } }
        }
        viewModelScope.launch { repository.syncLibraryIfStale() }
        viewModelScope.launch { loadLists() }
        loadCatalogs()
    }

    fun setTab(tab: AddNewTab) = _state.update { it.copy(tab = tab) }

    fun setScope(scope: SearchScope) {
        val query = _state.value.query
        _state.update { it.copy(scope = scope) }
        // A scope switch re-runs the search against the other catalog.
        if (query.isNotBlank()) submitSearch()
    }

    /** Stores the raw query; a blank query clears results back to the tabs. */
    fun onQueryChange(value: String) {
        _state.update {
            if (value.isBlank()) {
                it.copy(
                    query = value,
                    searched = false,
                    searching = false,
                    searchResults = SearchResults(),
                    searchError = null,
                )
            } else {
                it.copy(query = value)
            }
        }
    }

    /** Runs the lookup for the active scope. */
    fun submitSearch() {
        val current = _state.value
        if (current.query.isBlank() || current.searching) return
        _state.update { it.copy(searching = true, searchError = null) }
        viewModelScope.launch {
            val result = when (current.scope) {
                SearchScope.ARTIST -> repository.searchArtists(current.query).mapValue { artists ->
                    SearchResults(artists = artists)
                }

                SearchScope.ALBUM -> repository.searchAlbums(current.query).mapValue { albums ->
                    SearchResults(albums = albums)
                }
            }
            when (result) {
                is ArrResult.Ok -> _state.update {
                    it.copy(searching = false, searched = true, searchResults = result.value)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        searching = false,
                        searched = true,
                        searchResults = SearchResults(),
                        searchError = result.error,
                    )
                }
            }
        }
    }

    /** Opens the add sheet for one artist result, preselecting catalog defaults. */
    fun openArtistAddSheet(candidate: ArtistResource) {
        val current = _state.value
        _state.update {
            it.copy(
                albumSheet = null,
                artistSheet = ArtistAddSheetUiState(
                    candidate = candidate,
                    rootFolderPath = current.rootFolders.firstOrNull()?.path,
                    qualityProfileId = rootFolderDefault(
                        current.rootFolders,
                        current.qualityProfiles,
                    ) ?: current.qualityProfiles.firstOrNull()?.id,
                    metadataProfileId = current.metadataProfiles.firstOrNull()?.id,
                ),
            )
        }
    }

    fun updateArtistAddSheet(transform: (ArtistAddSheetUiState) -> ArtistAddSheetUiState) {
        _state.update { state ->
            state.artistSheet
                ?.let { sheet -> state.copy(artistSheet = transform(sheet)) }
                ?: state
        }
    }

    fun dismissArtistAddSheet() = _state.update { it.copy(artistSheet = null) }

    /** Adds the sheet's artist, optionally starting a missing-album search. */
    fun submitArtistAdd(searchForMissingAlbums: Boolean) {
        val sheet = _state.value.artistSheet ?: return
        if (!sheet.submittable || sheet.submitting) return
        updateArtistAddSheet { it.copy(submitting = true) }
        viewModelScope.launch {
            val payload = AddNewLogic.toAddArtistPayload(
                candidate = sheet.candidate,
                rootFolderPath = sheet.rootFolderPath.orEmpty(),
                qualityProfileId = sheet.qualityProfileId ?: 0,
                metadataProfileId = sheet.metadataProfileId ?: 0,
                monitor = sheet.monitor,
                tags = sheet.selectedTags,
                searchForMissingAlbums = searchForMissingAlbums,
            )
            val title = sheetName(sheet.candidate)
            when (val result = repository.addArtist(payload)) {
                is ArrResult.Ok -> {
                    val message = when (result.value) {
                        AddOutcome.Synced ->
                            if (searchForMissingAlbums) {
                                AddNewMessage.ArtistAddedAndSearching(title)
                            } else {
                                AddNewMessage.ArtistAdded(title)
                            }

                        is AddOutcome.Queued -> AddNewMessage.ArtistAddQueued(title)
                    }
                    _state.update { it.copy(artistSheet = null, message = message) }
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        artistSheet = sheet.copy(submitting = false),
                        message = AddNewMessage.ArtistAddFailed(title),
                    )
                }
            }
        }
    }

    /** Opens the standalone add sheet for one album result. */
    fun openAlbumAddSheet(candidate: AlbumResource) {
        _state.update {
            it.copy(
                artistSheet = null,
                albumSheet = AlbumAddSheetUiState(
                    candidate = candidate,
                    libraryArtist = matchArtist(it.libraryArtists, candidate),
                ),
            )
        }
    }

    fun updateAlbumAddSheet(transform: (AlbumAddSheetUiState) -> AlbumAddSheetUiState) {
        _state.update { state ->
            state.albumSheet
                ?.let { sheet -> state.copy(albumSheet = transform(sheet)) }
                ?: state
        }
    }

    fun dismissAlbumAddSheet() = _state.update { it.copy(albumSheet = null) }

    /** Adds the sheet's album under its library artist, optionally searching now. */
    fun submitAlbumAdd(searchForNewAlbum: Boolean) {
        val sheet = _state.value.albumSheet ?: return
        val artist = sheet.libraryArtist ?: return
        if (sheet.submitting) return
        updateAlbumAddSheet { it.copy(submitting = true) }
        viewModelScope.launch {
            val payload = AddNewLogic.toAddAlbumPayload(
                candidate = sheet.candidate,
                artistId = artist.id,
                monitored = sheet.monitored,
                searchForNewAlbum = searchForNewAlbum,
            )
            val title = sheet.candidate.title.orEmpty().ifEmpty {
                sheet.candidate.foreignAlbumId.orEmpty()
            }
            when (val result = repository.addAlbum(payload)) {
                is ArrResult.Ok -> {
                    val message = when (result.value) {
                        AddOutcome.Synced ->
                            if (searchForNewAlbum) {
                                AddNewMessage.AlbumAddedAndSearching(title)
                            } else {
                                AddNewMessage.AlbumAdded(title)
                            }

                        is AddOutcome.Queued -> AddNewMessage.AlbumAddQueued(title)
                    }
                    _state.update { it.copy(albumSheet = null, message = message) }
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        albumSheet = sheet.copy(submitting = false),
                        message = AddNewMessage.AlbumAddFailed(title),
                    )
                }
            }
        }
    }

    /** Opens the discography picker for one library artist and loads its albums. */
    fun openStudioPicker(artist: ArtistResource) {
        _state.update {
            it.copy(studioPicker = StudioPickerUiState(artist = artist))
        }
        viewModelScope.launch {
            when (val result = repository.discography(artist.id)) {
                is ArrResult.Ok -> _state.update { current ->
                    val picker = current.studioPicker ?: return@update current
                    if (picker.artist.id != artist.id) return@update current
                    current.copy(
                        studioPicker = picker.copy(
                            albums = result.value.sortedWith(
                                compareByDescending<AlbumResource> { it.releaseDate.orEmpty() }
                                    .thenBy { it.id },
                            ),
                            selection = AddNewLogic.preselect(result.value, picker.strategy),
                            loading = false,
                            error = null,
                        ),
                    )
                }

                is ArrResult.Err -> _state.update { current ->
                    val picker = current.studioPicker ?: return@update current
                    if (picker.artist.id != artist.id) return@update current
                    current.copy(
                        studioPicker = picker.copy(loading = false, error = result.error),
                    )
                }
            }
        }
    }

    fun updateStudioPicker(transform: (StudioPickerUiState) -> StudioPickerUiState) {
        _state.update { state ->
            state.studioPicker
                ?.let { picker -> state.copy(studioPicker = transform(picker)) }
                ?: state
        }
    }

    fun dismissStudioPicker() = _state.update { it.copy(studioPicker = null) }

    /** Flips one album's target monitored state in the picker. */
    fun toggleStudioAlbum(albumId: Int) = updateStudioPicker { picker ->
        val target = picker.selection[albumId]
            ?: picker.albums.firstOrNull { it.id == albumId }?.monitored
            ?: false
        picker.copy(selection = picker.selection + (albumId to !target))
    }

    /** Applies a bulk strategy as the picker's preselection. */
    fun setStudioStrategy(strategy: MonitorTypes) = updateStudioPicker { picker ->
        picker.copy(
            strategy = strategy,
            selection = AddNewLogic.preselect(picker.albums, strategy),
        )
    }

    /** Bulk action: target every album as monitored. */
    fun studioSelectAll() = updateStudioPicker { picker ->
        picker.copy(selection = picker.albums.associate { it.id to true })
    }

    /** Bulk action: target every album as unmonitored. */
    fun studioSelectNone() = updateStudioPicker { picker ->
        picker.copy(selection = picker.albums.associate { it.id to false })
    }

    /** Sends the discography selection through the album-studio endpoint. */
    fun submitStudio() {
        val picker = _state.value.studioPicker ?: return
        if (picker.applying || picker.albums.isEmpty()) return
        updateStudioPicker { it.copy(applying = true) }
        viewModelScope.launch {
            val payload = AddNewLogic.toAlbumStudioPayload(
                artistId = picker.artist.id,
                albums = picker.albums,
                targets = picker.selection,
                strategy = picker.strategy,
            )
            val artist = sheetName(picker.artist)
            val monitoredCount = picker.albums.count {
                picker.targetOf(it.id, it.monitored)
            }
            when (val result = repository.applyAlbumStudio(payload)) {
                is ArrResult.Ok -> {
                    val message = when (result.value) {
                        AddOutcome.Synced -> AddNewMessage.StudioApplied(artist, monitoredCount)
                        is AddOutcome.Queued -> AddNewMessage.StudioQueued(artist)
                    }
                    _state.update { it.copy(studioPicker = null, message = message) }
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        studioPicker = picker.copy(applying = false),
                        message = AddNewMessage.StudioFailed(artist),
                    )
                }
            }
        }
    }

    /** Pull-to-refresh and re-entry: catalogs, library, and lists. */
    fun refreshAll() {
        _state.update { it.copy(listsRefreshing = true) }
        viewModelScope.launch {
            repository.syncLibraryIfStale(ttlMillis = 0)
            loadLists(ttlMillis = 0)
        }
        loadCatalogs()
    }

    fun refreshLists() {
        _state.update { it.copy(listsRefreshing = true) }
        viewModelScope.launch { loadLists(ttlMillis = 0) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun loadLists(
        ttlMillis: Long = LidarrAddNewRepository.LIST_TTL_MILLIS,
    ) {
        when (val result = repository.importLists(ttlMillis)) {
            is ArrResult.Ok -> _state.update {
                it.copy(
                    lists = result.value.sortedBy { list -> list.name.orEmpty().lowercase() },
                    listsLoaded = true,
                    listsError = null,
                    listsRefreshing = false,
                )
            }

            is ArrResult.Err -> _state.update {
                it.copy(
                    listsLoaded = it.lists.isNotEmpty(),
                    listsError = result.error,
                    listsRefreshing = false,
                )
            }
        }
    }

    private fun loadCatalogs() {
        viewModelScope.launch {
            when (val result = repository.rootFolders()) {
                is ArrResult.Ok -> _state.update { it.copy(rootFolders = result.value) }
                is ArrResult.Err -> _state.update { it.copy(catalogsError = result.error) }
            }
        }
        viewModelScope.launch {
            when (val result = repository.qualityProfiles()) {
                is ArrResult.Ok -> _state.update { it.copy(qualityProfiles = result.value) }
                is ArrResult.Err -> _state.update { it.copy(catalogsError = result.error) }
            }
        }
        viewModelScope.launch {
            when (val result = repository.metadataProfiles()) {
                is ArrResult.Ok -> _state.update { it.copy(metadataProfiles = result.value) }
                is ArrResult.Err -> _state.update { it.copy(catalogsError = result.error) }
            }
        }
        viewModelScope.launch {
            when (val result = repository.tags()) {
                is ArrResult.Ok -> _state.update { it.copy(tags = result.value) }
                is ArrResult.Err -> _state.update { it.copy(catalogsError = result.error) }
            }
        }
    }

    /** The library artist one album candidate belongs to, or null. */
    private fun matchArtist(
        artists: List<ArtistResource>,
        candidate: AlbumResource,
    ): ArtistResource? {
        val foreignId = candidate.artist?.foreignArtistId ?: candidate.artist?.mbId
        return artists.firstOrNull { it.foreignArtistId == foreignId || it.mbId == foreignId }
    }

    private fun sheetName(artist: ArtistResource): String =
        artist.artistName.orEmpty().ifEmpty { artist.foreignArtistId.orEmpty() }

    /**
     * Root folders carry server-side default profile ids; the first folder's
     * default wins when it exists, so the sheet opens with the same values
     * the web client would preselect.
     */
    private fun rootFolderDefault(
        folders: List<RootFolderResource>,
        profiles: List<QualityProfileResource>,
    ): Int? = folders.firstOrNull()?.defaultQualityProfileId?.takeIf { default ->
        profiles.any { it.id == default }
    }

    private fun <T, R> ArrResult<T>.mapValue(transform: (T) -> R): ArrResult<R> = when (this) {
        is ArrResult.Ok -> ArrResult.ok(transform(value))
        is ArrResult.Err -> this
    }
}
