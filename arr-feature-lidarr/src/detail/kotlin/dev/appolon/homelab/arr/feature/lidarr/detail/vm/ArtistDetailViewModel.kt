package dev.appolon.homelab.arr.feature.lidarr.detail.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportUpdateResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.NewItemMonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityModel
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.lidarr.model.RenameTrackResource
import dev.appolon.homelab.arr.api.lidarr.model.RetagTrackResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackFileResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.feature.lidarr.detail.data.LidarrArtistDetailRepository
import dev.appolon.homelab.arr.feature.lidarr.detail.data.LidarrCommandBodies
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.library.data.parseTagIds
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

/** The two content tabs of the artist detail screen. */
enum class DetailTab { ALBUMS, HISTORY }

/**
 * The four release-type buckets the web UI groups albums under. Wire values
 * cover MusicBrainz primary album types; anything unfamiliar lands in OTHER.
 */
enum class AlbumReleaseType {
    ALBUM, SINGLE, EP, OTHER;

    companion object {
        fun of(raw: String?): AlbumReleaseType = when (raw?.trim()?.lowercase()) {
            "album" -> ALBUM
            "single" -> SINGLE
            "ep" -> EP
            else -> OTHER
        }
    }
}

/** One release-type bucket with its albums, newest release first. */
data class AlbumGroup(val type: AlbumReleaseType, val albums: List<AlbumResource>)

/** What the manual search sheet is currently searching for. */
sealed interface SearchTarget {
    data class Album(val albumId: Int, val label: String) : SearchTarget
    data object Artist : SearchTarget
}

/** Form state of the artist edit sheet. */
data class ArtistEditForm(
    val monitored: Boolean,
    val monitorNewItems: NewItemMonitorTypes,
    val qualityProfileId: Int,
    val metadataProfileId: Int,
    val path: String,
    val rootFolderPath: String,
    val tagsText: String,
    val moveFiles: Boolean = false,
    val tagsError: Boolean = false,
)

/** Form state of the track file editor. */
data class TrackFileForm(
    val fileId: Int,
    val trackId: Int,
    val qualityId: Int,
    val releaseGroup: String,
    val original: TrackFileResource,
)

/** Rename preview sheet state. */
data class RenamePreviewState(
    val rows: List<RenameTrackResource> = emptyList(),
    val loading: Boolean = true,
    val error: ArrError? = null,
)

/** Retag preview sheet state. */
data class RetagPreviewState(
    val rows: List<RetagTrackResource> = emptyList(),
    val loading: Boolean = true,
    val error: ArrError? = null,
)

/** Interactive import sheet state. */
data class ImportState(
    val candidates: List<ManualImportResource> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val loading: Boolean = true,
    val error: ArrError? = null,
)

/** Transient one-line result of a write, surfaced as a snackbar. */
sealed interface DetailMessage {
    data object WriteSynced : DetailMessage
    data object WriteQueued : DetailMessage
    data object SearchStarted : DetailMessage
    data object SearchQueued : DetailMessage
    data object GrabSent : DetailMessage
    data object GrabQueued : DetailMessage
    data class ImportSynced(val count: Int) : DetailMessage
    data object ImportQueued : DetailMessage
    data object RenameStarted : DetailMessage
    data object RenameQueued : DetailMessage
    data object RetagStarted : DetailMessage
    data object RetagQueued : DetailMessage
    data object DeleteSynced : DetailMessage
    data object DeleteQueued : DetailMessage
    data class WriteFailed(val error: ArrError) : DetailMessage
}

/**
 * State of the artist detail screen. Derived views (release-type groups, the
 * selected album's tracks, per-track history) are computed properties so
 * presentation logic stays testable without Compose.
 */
data class ArtistDetailUiState(
    val artistId: Int,
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val artist: ArtistResource? = null,
    val albums: List<AlbumResource> = emptyList(),
    val tracks: List<TrackResource> = emptyList(),
    val history: List<HistoryResource> = emptyList(),
    val historyRefreshing: Boolean = false,
    val qualityProfiles: List<QualityProfileResource> = emptyList(),
    val qualities: List<Quality> = emptyList(),
    val metadataProfiles: List<MetadataProfileResource> = emptyList(),
    val tags: List<TagResource> = emptyList(),
    val tab: DetailTab = DetailTab.ALBUMS,
    val selectedAlbumId: Int? = null,
    val searchTarget: SearchTarget? = null,
    val releases: List<ReleaseResource> = emptyList(),
    val releasesLoading: Boolean = false,
    val releasesError: ArrError? = null,
    val fileForm: TrackFileForm? = null,
    val editForm: ArtistEditForm? = null,
    val renamePreview: RenamePreviewState? = null,
    val retagPreview: RetagPreviewState? = null,
    val importState: ImportState? = null,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    val queuedForServer: Int = 0,
    val queuedForArtist: Boolean = false,
    val refreshing: Boolean = false,
    val message: DetailMessage? = null,
) {
    /** Albums bucketed by release type, group order matching the web UI. */
    val albumGroups: List<AlbumGroup>
        get() {
            val order = listOf(
                AlbumReleaseType.ALBUM,
                AlbumReleaseType.SINGLE,
                AlbumReleaseType.EP,
                AlbumReleaseType.OTHER,
            )
            return order.mapNotNull { type ->
                val members = albums
                    .filter { AlbumReleaseType.of(it.albumType) == type }
                    .sortedWith(
                        compareByDescending<AlbumResource> {
                            ArrTime.parse(it.releaseDate)?.toEpochMilli() ?: 0L
                        }.thenBy { it.title.orEmpty() },
                    )
                if (members.isEmpty()) null else AlbumGroup(type, members)
            }
        }

    fun albumOf(albumId: Int): AlbumResource? = albums.firstOrNull { it.id == albumId }

    /** Tracks of one album in disc and track order. */
    fun tracksOf(albumId: Int): List<TrackResource> =
        tracks.filter { it.albumId == albumId }
            .sortedWith(
                compareBy(
                    { it.mediumNumber },
                    { it.trackNumber?.toIntOrNull() ?: it.absoluteTrackNumber },
                ),
            )

    fun historyFor(trackId: Int): List<HistoryResource> =
        history.filter { it.trackId == trackId }.sortedByDescending { it.date }

    fun qualityLabel(id: Int): String =
        qualities.firstOrNull { it.id == id }?.name ?: "#$id"

    fun profileLabel(id: Int): String =
        qualityProfiles.firstOrNull { it.id == id }?.name ?: "#$id"

    fun metadataProfileLabel(id: Int): String =
        metadataProfiles.firstOrNull { it.id == id }?.name ?: "#$id"

    fun tagLabel(id: Int): String = tags.firstOrNull { it.id == id }?.label ?: "#$id"
}

/**
 * ViewModel of the Lidarr artist detail screen. Owns no Android types so
 * unit tests construct it directly against fakes; the destination composable
 * wires it to the data graph singletons.
 */
class ArtistDetailViewModel(
    private val artistId: Int,
    private val repository: LidarrArtistDetailRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtistDetailUiState(artistId = artistId))
    val state: StateFlow<ArtistDetailUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<ArtistDetailUiState>> = state
        .map { s ->
            when {
                s.deleted -> LceState.Empty
                s.loadError != null && s.artist == null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.artist == null -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeArtist(artistId)
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { artist ->
                    _state.update { s ->
                        s.copy(
                            artist = artist,
                            loaded = true,
                            selectedAlbumId = s.selectedAlbumId ?: defaultAlbumOf(artist, s.albums),
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.observeAlbums(artistId)
                .catch { }
                .collect { albums ->
                    _state.update { s ->
                        s.copy(
                            albums = albums,
                            selectedAlbumId = s.selectedAlbumId
                                ?: defaultAlbumOf(s.artist, albums),
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.observeTracks(artistId)
                .catch { }
                .collect { tracks -> _state.update { it.copy(tracks = tracks) } }
        }
        viewModelScope.launch {
            repository.observeHistory(artistId)
                .catch { }
                .collect { history -> _state.update { it.copy(history = history) } }
        }
        viewModelScope.launch {
            repository.queuedCount()
                .catch { }
                .collect { count -> _state.update { it.copy(queuedForServer = count) } }
        }
        viewModelScope.launch {
            repository.queuedArtistIds()
                .catch { }
                .collect { ids ->
                    _state.update { it.copy(queuedForArtist = artistId in ids) }
                }
        }
        viewModelScope.launch { initialLoad() }
    }

    /** Cache-first entry: refreshes only when the shared cache is absent or stale. */
    private suspend fun initialLoad() {
        val cached = repository.cachedArtist(artistId)
        if (cached == null || repository.artistStale()) {
            refreshAll()
        } else {
            refreshLookups()
        }
    }

    /** Pull-to-refresh and error retry: refetch everything for this artist. */
    fun refresh() {
        viewModelScope.launch { refreshAll() }
    }

    private suspend fun refreshAll() {
        _state.update { it.copy(refreshing = true) }
        when (val artist = repository.refreshArtist(artistId)) {
            is ArrResult.Err -> _state.update {
                it.copy(refreshing = false, loadError = artist.error)
            }

            is ArrResult.Ok -> {
                _state.update { it.copy(refreshing = false, loadError = null) }
                refreshAlbums()
                refreshTracks()
                refreshHistoryFeed()
                refreshLookups()
            }
        }
    }

    private suspend fun refreshAlbums() {
        when (val result = repository.refreshAlbums(artistId)) {
            is ArrResult.Err -> if (_state.value.albums.isEmpty()) {
                _state.update { it.copy(loadError = result.error) }
            }

            is ArrResult.Ok -> Unit
        }
    }

    private suspend fun refreshTracks() {
        when (val result = repository.refreshTracks(artistId)) {
            is ArrResult.Err -> if (_state.value.tracks.isEmpty()) {
                _state.update { it.copy(loadError = result.error) }
            }

            is ArrResult.Ok -> Unit
        }
    }

    fun setTab(tab: DetailTab) = _state.update { it.copy(tab = tab) }

    fun selectAlbum(albumId: Int?) = _state.update { it.copy(selectedAlbumId = albumId) }

    fun toggleArtistMonitored() {
        val artist = _state.value.artist ?: return
        viewModelScope.launch {
            handleOutcome(
                prefix = Prefix.WRITE,
                outcome = repository.setArtistMonitored(artist, !artist.monitored),
            )
        }
    }

    fun toggleAlbumMonitored(album: AlbumResource) {
        viewModelScope.launch {
            handleOutcome(
                prefix = Prefix.WRITE,
                outcome = repository.setAlbumsMonitored(listOf(album.id), !album.monitored),
            )
        }
    }

    fun refreshMetadata() = runCommand(
        LidarrCommandBodies.refreshArtist(artistId),
        "Refresh artist metadata",
    )

    fun scanDisk() = runCommand(LidarrCommandBodies.rescanArtist(artistId), "Rescan artist folder")

    fun searchArtist() = runCommand(
        LidarrCommandBodies.artistSearch(artistId),
        "Search artist",
    )

    fun searchAlbum(albumId: Int) = runCommand(
        LidarrCommandBodies.albumSearch(albumId),
        "Search album",
    )

    private fun runCommand(body: String, description: String) {
        viewModelScope.launch {
            handleOutcome(Prefix.SEARCH, repository.runCommandBody(body, description))
        }
    }

    fun openManualSearchArtist() {
        _state.update {
            it.copy(
                searchTarget = SearchTarget.Artist,
                releases = emptyList(),
                releasesLoading = true,
                releasesError = null,
            )
        }
        loadReleases()
    }

    fun openManualSearchAlbum(album: AlbumResource) {
        _state.update {
            it.copy(
                searchTarget = SearchTarget.Album(album.id, album.title.orEmpty()),
                releases = emptyList(),
                releasesLoading = true,
                releasesError = null,
            )
        }
        loadReleases()
    }

    fun closeManualSearch() =
        _state.update { it.copy(searchTarget = null, releases = emptyList(), releasesError = null) }

    fun refreshReleases() {
        _state.update { it.copy(releasesLoading = true, releasesError = null) }
        loadReleases()
    }

    private fun loadReleases() {
        val target = _state.value.searchTarget ?: return
        viewModelScope.launch {
            val result = repository.releases(
                artistId = artistId,
                albumId = (target as? SearchTarget.Album)?.albumId,
            )
            _state.update { s ->
                when (result) {
                    is ArrResult.Ok -> s.copy(
                        releases = result.value.sortedByDescending { it.ageMinutes },
                        releasesLoading = false,
                    )

                    is ArrResult.Err -> s.copy(
                        releasesLoading = false,
                        releasesError = result.error,
                    )
                }
            }
        }
    }

    fun grabRelease(release: ReleaseResource) {
        viewModelScope.launch {
            handleOutcome(Prefix.GRAB, repository.grabRelease(release))
        }
    }

    /** Opens the rename preview for one album, or the whole artist when null. */
    fun openRenamePreview(albumId: Int?) {
        _state.update { it.copy(renamePreview = RenamePreviewState()) }
        viewModelScope.launch {
            _state.update { s ->
                when (val result = repository.renamePreview(artistId, albumId)) {
                    is ArrResult.Ok -> s.copy(
                        renamePreview = RenamePreviewState(rows = result.value, loading = false),
                    )

                    is ArrResult.Err -> s.copy(
                        renamePreview = RenamePreviewState(loading = false, error = result.error),
                    )
                }
            }
        }
    }

    fun closeRenamePreview() = _state.update { it.copy(renamePreview = null) }

    /** Runs the rename over the previewed files; the preview sheet closes on dispatch. */
    fun runRename() {
        val preview = _state.value.renamePreview ?: return
        if (preview.rows.isEmpty()) return
        viewModelScope.launch {
            val outcome = repository.runCommandBody(
                LidarrCommandBodies.renameFiles(preview.rows.map { it.trackFileId }),
                "Rename track files",
            )
            closeRenamePreview()
            handleOutcome(Prefix.RENAME, outcome)
        }
    }

    /** Opens the retag preview for one album, or the whole artist when null. */
    fun openRetagPreview(albumId: Int?) {
        _state.update { it.copy(retagPreview = RetagPreviewState()) }
        viewModelScope.launch {
            _state.update { s ->
                when (val result = repository.retagPreview(artistId, albumId)) {
                    is ArrResult.Ok -> s.copy(
                        retagPreview = RetagPreviewState(rows = result.value, loading = false),
                    )

                    is ArrResult.Err -> s.copy(
                        retagPreview = RetagPreviewState(loading = false, error = result.error),
                    )
                }
            }
        }
    }

    fun closeRetagPreview() = _state.update { it.copy(retagPreview = null) }

    /** Runs the retag over the previewed files; the preview sheet closes on dispatch. */
    fun runRetag() {
        val preview = _state.value.retagPreview ?: return
        if (preview.rows.isEmpty()) return
        viewModelScope.launch {
            val outcome = repository.runCommandBody(
                LidarrCommandBodies.retagFiles(preview.rows.map { it.trackFileId }),
                "Retag track files",
            )
            closeRetagPreview()
            handleOutcome(Prefix.RETAG, outcome)
        }
    }

    fun openImport() {
        _state.update { it.copy(importState = ImportState()) }
        viewModelScope.launch {
            val artist = _state.value.artist
            val result = repository.importCandidates(
                folder = artist?.path,
                artistId = artistId,
            )
            _state.update { s ->
                val current = s.importState ?: return@update s
                when (result) {
                    is ArrResult.Ok -> s.copy(
                        importState = current.copy(
                            candidates = result.value,
                            loading = false,
                        ),
                    )

                    is ArrResult.Err -> s.copy(
                        importState = current.copy(loading = false, error = result.error),
                    )
                }
            }
        }
    }

    fun closeImport() = _state.update { it.copy(importState = null) }

    fun toggleImportSelected(id: Int) = _state.update { s ->
        val current = s.importState ?: return@update s
        val next = if (id in current.selected) current.selected - id else current.selected + id
        s.copy(importState = current.copy(selected = next))
    }

    fun importSelected() {
        val current = _state.value.importState ?: return
        val chosen = current.candidates.filter { it.id in current.selected }
        if (chosen.isEmpty()) return
        val payload = chosen.map { it.toUpdatePayload(artistId) }
        viewModelScope.launch {
            when (val outcome = repository.importItems(payload)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        importState = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            DetailMessage.ImportQueued
                        } else {
                            DetailMessage.ImportSynced(chosen.size)
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(message = DetailMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    fun openFileEditor(track: TrackResource) {
        val file = track.trackFile ?: return
        _state.update {
            it.copy(
                fileForm = TrackFileForm(
                    fileId = file.id,
                    trackId = track.id,
                    qualityId = file.quality?.quality?.id ?: 0,
                    releaseGroup = file.releaseGroup.orEmpty(),
                    original = file,
                ),
            )
        }
    }

    fun closeFileEditor() = _state.update { it.copy(fileForm = null) }

    fun updateFileEditor(transform: (TrackFileForm) -> TrackFileForm) =
        _state.update { s ->
            val form = s.fileForm ?: return@update s
            s.copy(fileForm = transform(form))
        }

    fun submitFileEditor() {
        val form = _state.value.fileForm ?: return
        val state = _state.value
        val file = form.original.copy(
            quality = QualityModel(
                quality = Quality(
                    id = form.qualityId,
                    name = state.qualities.firstOrNull { it.id == form.qualityId }?.name,
                ),
            ),
            releaseGroup = form.releaseGroup.trim().ifEmpty { null },
        )
        viewModelScope.launch {
            handleOutcome(Prefix.WRITE, repository.updateTrackFile(file))
            _state.update { it.copy(fileForm = null) }
        }
    }

    fun deleteFileFromEditor() {
        val form = _state.value.fileForm ?: return
        viewModelScope.launch {
            handleOutcome(Prefix.WRITE, repository.deleteTrackFile(form.fileId))
            _state.update { it.copy(fileForm = null) }
        }
    }

    fun openEdit() {
        val artist = _state.value.artist ?: return
        _state.update {
            it.copy(
                editForm = ArtistEditForm(
                    monitored = artist.monitored,
                    monitorNewItems = artist.monitorNewItems,
                    qualityProfileId = artist.qualityProfileId,
                    metadataProfileId = artist.metadataProfileId,
                    path = artist.path.orEmpty(),
                    rootFolderPath = artist.rootFolderPath.orEmpty(),
                    tagsText = artist.tags.orEmpty().sorted().joinToString(","),
                ),
            )
        }
    }

    fun closeEdit() = _state.update { it.copy(editForm = null) }

    fun updateEdit(transform: (ArtistEditForm) -> ArtistEditForm) =
        _state.update { s ->
            val form = s.editForm ?: return@update s
            s.copy(editForm = transform(form))
        }

    fun submitEdit() {
        val form = _state.value.editForm ?: return
        val artist = _state.value.artist ?: return
        val tagIds = parseTagIds(form.tagsText)
        if (tagIds == null) {
            _state.update { s -> s.copy(editForm = form.copy(tagsError = true)) }
            return
        }
        val edited = artist.copy(
            monitored = form.monitored,
            monitorNewItems = form.monitorNewItems,
            qualityProfileId = form.qualityProfileId,
            metadataProfileId = form.metadataProfileId,
            path = form.path.trim().ifEmpty { null },
            rootFolderPath = form.rootFolderPath.trim().ifEmpty { null },
            tags = if (form.tagsText.isBlank()) artist.tags else tagIds,
        )
        viewModelScope.launch {
            handleOutcome(Prefix.WRITE, repository.updateArtist(edited, form.moveFiles))
            _state.update { it.copy(editForm = null) }
        }
    }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }

    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    fun confirmDelete(deleteFiles: Boolean, addImportListExclusion: Boolean) {
        _state.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            val outcome = repository.deleteArtist(
                artistId,
                deleteFiles,
                addImportListExclusion,
            )
            when (outcome) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        deleted = true,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            DetailMessage.DeleteQueued
                        } else {
                            DetailMessage.DeleteSynced
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(message = DetailMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _state.update { it.copy(historyRefreshing = true) }
            repository.refreshHistory(artistId)
            _state.update { it.copy(historyRefreshing = false) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun refreshLookups() {
        repository.qualityProfiles().let { result ->
            if (result is ArrResult.Ok) {
                _state.update { it.copy(qualityProfiles = result.value) }
            }
        }
        repository.qualityDefinitions().let { result ->
            if (result is ArrResult.Ok) {
                _state.update {
                    it.copy(
                        qualities = result.value
                            .mapNotNull { def -> def.quality?.takeIf { q -> q.id != 0 } },
                    )
                }
            }
        }
        repository.metadataProfiles().let { result ->
            if (result is ArrResult.Ok) {
                _state.update { it.copy(metadataProfiles = result.value) }
            }
        }
        repository.tags().let { result ->
            if (result is ArrResult.Ok) {
                _state.update { it.copy(tags = result.value) }
            }
        }
    }

    private suspend fun refreshHistoryFeed() {
        repository.refreshHistory(artistId)
    }

    private fun handleOutcome(prefix: Prefix, outcome: ArrResult<WriteOutcome>) {
        _state.update { s ->
            when (outcome) {
                is ArrResult.Ok -> s.copy(
                    message = when {
                        outcome.value is WriteOutcome.Queued && prefix == Prefix.SEARCH ->
                            DetailMessage.SearchQueued

                        outcome.value is WriteOutcome.Queued && prefix == Prefix.GRAB ->
                            DetailMessage.GrabQueued

                        outcome.value is WriteOutcome.Queued && prefix == Prefix.RENAME ->
                            DetailMessage.RenameQueued

                        outcome.value is WriteOutcome.Queued && prefix == Prefix.RETAG ->
                            DetailMessage.RetagQueued

                        outcome.value is WriteOutcome.Queued -> DetailMessage.WriteQueued

                        prefix == Prefix.SEARCH -> DetailMessage.SearchStarted
                        prefix == Prefix.GRAB -> DetailMessage.GrabSent
                        prefix == Prefix.RENAME -> DetailMessage.RenameStarted
                        prefix == Prefix.RETAG -> DetailMessage.RetagStarted
                        else -> DetailMessage.WriteSynced
                    },
                )

                is ArrResult.Err -> s.copy(message = DetailMessage.WriteFailed(outcome.error))
            }
        }
    }

    private enum class Prefix { WRITE, SEARCH, GRAB, RENAME, RETAG }

    private fun defaultAlbumOf(artist: ArtistResource?, albums: List<AlbumResource>): Int? {
        if (artist == null) return null
        return albums.firstOrNull()?.id
    }
}

/** Maps one import candidate onto the update payload the import call takes. */
internal fun ManualImportResource.toUpdatePayload(artistId: Int): ManualImportUpdateResource =
    ManualImportUpdateResource(
        id = id,
        path = path,
        name = name,
        artistId = artistId,
        albumId = album?.id,
        albumReleaseId = albumReleaseId,
        tracks = tracks,
        trackIds = tracks.orEmpty().map { it.id },
        quality = quality,
        releaseGroup = releaseGroup,
        indexerFlags = indexerFlags,
        downloadId = downloadId,
        additionalFile = additionalFile,
        replaceExistingFiles = replaceExistingFiles,
        disableReleaseSwitching = disableReleaseSwitching,
    )
