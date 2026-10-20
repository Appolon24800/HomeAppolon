package dev.appolon.homelab.arr.feature.lidarr.detail.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrLibrary
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStatisticsResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.NewItemMonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityModel
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.lidarr.model.RenameTrackResource
import dev.appolon.homelab.arr.api.lidarr.model.RetagTrackResource
import dev.appolon.homelab.arr.api.lidarr.model.TagDifference
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackFileResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.detail.FakeActionQueue
import dev.appolon.homelab.arr.feature.lidarr.detail.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.detail.data.LidarrArtistDetailRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the detail ViewModel's LCE mapping, release-type grouping, track
 * loading, every sheet flow (edit, file editor, rename and retag previews,
 * manual search, interactive import), offline queue indicators, and delete.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val library = FakeLidarrLibrary()
    private val queue = FakeLidarrQueue()
    private val store = FakeEntityStore()
    private val actionQueue = FakeActionQueue()
    private val serverId = ServerId("server-1")
    private val postedCommands = mutableListOf<String>()
    private var posterResult: ArrResult<Unit> = ArrResult.ok(Unit)

    private val repository = LidarrArtistDetailRepository(
        serverId = serverId,
        store = store,
        artistApi = library,
        albumApi = library,
        trackApi = library,
        trackFileApi = library,
        renameTrackApi = library,
        retagTrackApi = library,
        historyApi = queue,
        releaseApi = queue,
        manualImportApi = queue,
        qualityProfileApi = { listOf(QualityProfileResource(id = 1, name = "High quality")) },
        qualityDefinitionApi = {
            listOf(
                QualityDefinitionResource(id = 1, quality = Quality(id = 1, name = "MP3-320")),
                QualityDefinitionResource(id = 2, quality = Quality(id = 2, name = "FLAC")),
            )
        },
        metadataProfileApi = { listOf(MetadataProfileResource(id = 1, name = "Standard")) },
        tagApi = { listOf(TagResource(id = 4, label = "vinyl")) },
        commandPoster = { body ->
            postedCommands.add(body)
            posterResult
        },
        actionQueue = actionQueue,
        ioDispatcher = dispatcher,
    )

    private lateinit var vm: ArtistDetailViewModel
    private var latestLce: LceState<ArtistDetailUiState> = LceState.Loading
    private val collectors = CoroutineScope(dispatcher + SupervisorJob())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        library.artists[7] = artist()
        library.albums[50] = album(50, "Studio", "Album", "2024-03-01T00:00:00Z", monitored = true)
        library.albums[51] =
            album(51, "Latest", "Single", "2026-01-09T00:00:00Z", monitored = false)
        library.albums[52] = album(52, "Live EP", "EP", "2020-05-05T00:00:00Z", monitored = false)
        library.tracks[101] = track(101, albumId = 50, number = "1")
        library.tracks[102] = track(102, albumId = 50, number = "2", explicit = true)
        library.trackFiles[301] = file()
        library.renames.add(
            RenameTrackResource(
                id = 1,
                artistId = 7,
                albumId = 50,
                trackFileId = 301,
                existingPath = "/music/old.mp3",
                newPath = "/music/01 - Track.mp3",
            ),
        )
        library.retags.add(
            RetagTrackResource(
                id = 1,
                artistId = 7,
                albumId = 50,
                trackFileId = 301,
                path = "/music/01 - Track.mp3",
                changes = listOf(
                    TagDifference(field = "title", oldValue = "Old", newValue = "New"),
                ),
            ),
        )
        queue.history[1] = history(1, trackId = 101)
        queue.releases[1] = ReleaseResource(
            guid = "g1",
            artistId = 7,
            albumId = 50,
            title = "Belle - Studio FLAC",
            indexer = "indexer1",
            seeders = 12,
        )
        queue.manualImports[9] = ManualImportResource(
            id = 9,
            path = "/music/Belle/candidate.flac",
            name = "candidate.flac",
            artist = ArtistResource(id = 7),
            album = AlbumResource(id = 50),
        )
        library.error = { null }
        queue.error = { null }
        vm = ArtistDetailViewModel(7, repository)
        // stateIn only computes while subscribed; keep one eager collector.
        collectors.launch { vm.lce.collect { latestLce = it } }
    }

    @After
    fun tearDown() {
        collectors.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load renders artist groups albums by release type and tracks`() = runTest {
        val state = vm.state.value
        assertEquals("Belle", state.artist?.artistName)
        // Groups appear in web UI order: albums, singles, EPs, then other.
        assertEquals(
            listOf(AlbumReleaseType.ALBUM, AlbumReleaseType.SINGLE, AlbumReleaseType.EP),
            state.albumGroups.map { it.type },
        )
        // The newest single sorts before the older one within its group.
        val singles = state.albumGroups.first { it.type == AlbumReleaseType.SINGLE }
        assertEquals(listOf(51), singles.albums.map { it.id })
        // The first album is selected by default and its tracks load in order.
        assertEquals(50, state.selectedAlbumId)
        assertEquals(listOf("1", "2"), state.tracksOf(50).map { it.trackNumber })
        assertTrue(state.tracksOf(50).first { it.id == 102 }.explicit)
        assertEquals(listOf("High quality"), state.qualityProfiles.map { it.name })
        assertEquals(2, state.qualities.size)
        assertTrue(latestLce is LceState.Content)
    }

    @Test
    fun `album monitoring toggle flips the row`() = runTest {
        vm.toggleAlbumMonitored(library.albums.getValue(51))

        assertTrue(library.albums[51]?.monitored == true)
        // The optimistic write-through re-emits through the observer.
        assertTrue(vm.state.value.albums.first { it.id == 51 }.monitored)
    }

    @Test
    fun `album search dispatches AlbumSearch command`() = runTest {
        vm.searchAlbum(50)

        assertTrue(postedCommands.single().contains("AlbumSearch"))
        assertTrue(postedCommands.single().contains("[50]"))
    }

    @Test
    fun `file editor opens populated and submits quality`() = runTest {
        vm.openFileEditor(library.tracks.getValue(101))

        val form = vm.state.value.fileForm
        assertNotNull(form)
        assertEquals(301, form?.fileId)
        assertEquals(1, form?.qualityId)

        vm.updateFileEditor { it.copy(qualityId = 2) }
        vm.submitFileEditor()

        assertEquals("FLAC", library.trackFiles[301]?.quality?.quality?.name)
        assertNull(vm.state.value.fileForm)
    }

    @Test
    fun `file editor offline delete queues and track shows missing`() = runTest {
        vm.openFileEditor(library.tracks.getValue(101))
        library.error = { op -> if (op == "deleteTrackFile") IOException("down") else null }

        vm.deleteFileFromEditor()

        assertTrue(vm.state.value.message is DetailMessage.WriteQueued)
        assertFalse(vm.state.value.tracksOf(50).first { it.id == 101 }.hasFile)
    }

    @Test
    fun `rename preview loads rows and run posts RenameFiles`() = runTest {
        vm.openRenamePreview(albumId = 50)
        val paths = vm.state.value.renamePreview?.rows?.map { it.newPath }
        assertEquals(listOf("/music/01 - Track.mp3"), paths)

        vm.runRename()

        assertTrue(postedCommands.single().contains("RenameFiles"))
        assertTrue(postedCommands.single().contains("301"))
        assertNull(vm.state.value.renamePreview)
        assertEquals(DetailMessage.RenameStarted, vm.state.value.message)
    }

    @Test
    fun `retag preview loads differences and run posts RetagFiles`() = runTest {
        vm.openRetagPreview(albumId = 50)
        val rows = vm.state.value.retagPreview?.rows
        assertEquals(1, rows?.size)
        assertEquals("title", rows?.single()?.changes?.single()?.field)

        vm.runRetag()

        assertTrue(postedCommands.single().contains("RetagFiles"))
        assertNull(vm.state.value.retagPreview)
        assertEquals(DetailMessage.RetagStarted, vm.state.value.message)
    }

    @Test
    fun `manual search loads releases and grab queues offline`() = runTest {
        vm.openManualSearchAlbum(library.albums.getValue(50))
        assertEquals(1, vm.state.value.releases.size)

        queue.error = { op -> if (op == "grabRelease") IOException("down") else null }
        vm.grabRelease(vm.state.value.releases.first())

        assertEquals(DetailMessage.GrabQueued, vm.state.value.message)
        assertTrue(vm.state.value.queuedForArtist)
    }

    @Test
    fun `interactive import selects candidates and imports`() = runTest {
        vm.openImport()
        assertEquals(1, vm.state.value.importState?.candidates?.size)

        vm.toggleImportSelected(9)
        vm.importSelected()

        assertEquals(1, queue.manualImportUpdates.size)
        assertEquals(7, queue.manualImportUpdates.single().artistId)
        assertNull(vm.state.value.importState)
        assertEquals(DetailMessage.ImportSynced(1), vm.state.value.message)
    }

    @Test
    fun `edit sheet rejects bad tags then saves`() = runTest {
        vm.openEdit()
        vm.updateEdit { it.copy(tagsText = "one, two") }
        vm.submitEdit()
        assertTrue(vm.state.value.editForm?.tagsError == true)

        vm.updateEdit { it.copy(tagsText = "4", monitored = false, moveFiles = true) }
        vm.submitEdit()

        assertFalse(library.artists[7]?.monitored == true)
        assertEquals(listOf(4), library.artists[7]?.tags)
        assertNull(vm.state.value.editForm)
        assertEquals(DetailMessage.WriteSynced, vm.state.value.message)
    }

    @Test
    fun `delete sets deleted and navigates away`() = runTest {
        vm.confirmDelete(deleteFiles = true, addImportListExclusion = false)

        assertTrue(vm.state.value.deleted)
        assertEquals(DetailMessage.DeleteSynced, vm.state.value.message)
        assertTrue(latestLce is LceState.Empty)
    }

    @Test
    fun `history tab lists events newest first`() = runTest {
        queue.history[2] = history(2, trackId = 102).copy(date = "2026-10-09T00:00:00Z")
        vm.refreshHistory()

        assertEquals(listOf(2, 1), vm.state.value.history.map { it.id })
        assertEquals(1, vm.state.value.historyFor(101).size)
    }

    @Test
    fun `unknown release type lands in other bucket`() = runTest {
        library.albums[53] =
            album(53, "Broadcast", "Broadcast", "2022-02-02T00:00:00Z", monitored = false)
        vm.refresh()

        val groups = vm.state.value.albumGroups
        assertEquals(
            listOf(
                AlbumReleaseType.ALBUM,
                AlbumReleaseType.SINGLE,
                AlbumReleaseType.EP,
                AlbumReleaseType.OTHER,
            ),
            groups.map { it.type },
        )
    }

    private fun artist() = ArtistResource(
        id = 7,
        artistName = "Belle",
        monitored = true,
        qualityProfileId = 1,
        metadataProfileId = 1,
        path = "/music/Belle",
        genres = listOf("Pop"),
        links = emptyList(),
        tags = listOf(4),
        monitorNewItems = NewItemMonitorTypes.ALL,
    )

    private fun album(
        id: Int,
        title: String,
        type: String,
        releaseDate: String,
        monitored: Boolean,
    ) = AlbumResource(
            id = id,
            title = title,
            artistId = 7,
            albumType = type,
            monitored = monitored,
            profileId = 1,
            releaseDate = releaseDate,
            statistics = AlbumStatisticsResource(
                trackCount = 2,
                totalTrackCount = 2,
                trackFileCount = 1,
                sizeOnDisk = 4096,
                percentOfTracks = 50.0,
            ),
        )

    private fun track(id: Int, albumId: Int, number: String, explicit: Boolean = false) =
        TrackResource(
            id = id,
            artistId = 7,
            albumId = albumId,
            trackNumber = number,
            title = "Track $number",
            duration = 210_000,
            explicit = explicit,
            mediumNumber = 1,
            hasFile = id == 101,
            trackFileId = if (id == 101) 301 else 0,
            trackFile = if (id == 101) file() else null,
        )

    private fun file() = TrackFileResource(
        id = 301,
        artistId = 7,
        albumId = 50,
        path = "/music/Belle/Studio/01 - Track 1.mp3",
        size = 4096,
        quality = QualityModel(quality = Quality(id = 1, name = "MP3-320")),
    )

    private fun history(id: Int, trackId: Int) = HistoryResource(
        id = id,
        artistId = 7,
        albumId = 50,
        trackId = trackId,
        sourceTitle = "Belle - Studio",
        eventType = EntityHistoryEventType.TRACK_FILE_IMPORTED,
        date = "2026-10-0${id}T00:00:00Z",
        quality = QualityModel(quality = Quality(id = 1, name = "MP3-320")),
    )
}
