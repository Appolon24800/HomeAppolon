package dev.appolon.homelab.arr.feature.lidarr.detail.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrLibrary
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStatisticsResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
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
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.detail.FakeActionQueue
import dev.appolon.homelab.arr.feature.lidarr.detail.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the detail repository's cache-first reads, write-through syncs, and
 * every offline queueing path: artist and album monitoring, the track file
 * editor, artist delete, rename and retag previews, release grabs,
 * interactive import, commands, and the queued badge mapping.
 */
class LidarrArtistDetailRepositoryTest {

    private val library = FakeLidarrLibrary()
    private val queue = FakeLidarrQueue()
    private val store = FakeEntityStore()
    private val actionQueue = FakeActionQueue()
    private val serverId = ServerId("server-1")
    private var profileFetches = 0
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
        qualityProfileApi = {
            profileFetches++
            listOf(QualityProfileResource(id = 1, name = "High quality"))
        },
        qualityDefinitionApi = {
            listOf(
                QualityDefinitionResource(id = 1, quality = Quality(id = 1, name = "MP3-320")),
                QualityDefinitionResource(id = 2, quality = Quality(id = 2, name = "FLAC")),
            )
        },
        metadataProfileApi = { listOf(MetadataProfileResource(id = 1, name = "Standard")) },
        tagApi = { listOf(TagResource(id = 4, label = "vinyl")) },
        commandPoster = { _ -> posterResult },
        actionQueue = actionQueue,
    )

    @Before
    fun seed() {
        library.artists[7] = artist()
        library.albums[50] = album(50, "Studio", "Album", monitored = true)
        library.albums[51] = album(51, "Latest", "Single", monitored = false)
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
        library.error = { null }
        queue.error = { null }
    }

    @Test
    fun `refresh caches artist albums and tracks with files`() = runTest {
        assertTrue(repository.refreshArtist(7) is ArrResult.Ok)
        assertTrue(repository.refreshAlbums(7) is ArrResult.Ok)
        assertTrue(repository.refreshTracks(7) is ArrResult.Ok)

        assertEquals("Belle", repository.cachedArtist(7)?.artistName)
        assertEquals(listOf(50, 51), repository.observeAlbums(7).first().map { it.id })
        val cached = repository.observeTracks(7).first()
        assertTrue(cached.first().hasFile)
        assertEquals("MP3-320", cached.first().trackFile?.quality?.quality?.name)
    }

    @Test
    fun `stale cache flags trigger refetch`() = runTest {
        assertTrue(repository.artistStale())
        repository.refreshArtist(7)
        assertFalse(repository.artistStale())
    }

    @Test
    fun `artist monitored toggle syncs and writes through`() = runTest {
        val outcome = repository.setArtistMonitored(artist(), monitored = false)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertFalse(library.artists[7]?.monitored == true)
        assertFalse(repository.cachedArtist(7)?.monitored == true)
        assertTrue(actionQueue.pendingFlow.value.isEmpty())
    }

    @Test
    fun `artist monitored toggle offline queues with optimistic cache`() = runTest {
        library.error = { op -> if (op == "updateArtist") IOException("down") else null }

        val outcome = repository.setArtistMonitored(artist(), monitored = false)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = actionQueue.pendingFlow.value.single()
        assertEquals("PUT", action.method)
        assertEquals("api/v1/artist/7", action.path)
        assertFalse(repository.cachedArtist(7)?.monitored == true)
    }

    @Test
    fun `album monitoring offline queues and flips rows`() = runTest {
        repository.refreshAlbums(7)
        library.error = { op -> if (op == "monitorAlbums") IOException("down") else null }

        val outcome = repository.setAlbumsMonitored(listOf(51), monitored = true)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/album/monitor", actionQueue.pendingFlow.value.single().path)
        assertTrue(repository.observeAlbums(7).first().first { it.id == 51 }.monitored)
    }

    @Test
    fun `track file editor offline queues and updates cache`() = runTest {
        repository.refreshTracks(7)
        library.error = { op -> if (op == "updateTrackFile") IOException("down") else null }
        val edited = file().copy(
            quality = QualityModel(quality = Quality(id = 2, name = "FLAC")),
            releaseGroup = "GRP",
        )

        val outcome = repository.updateTrackFile(edited)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/trackfile/301", actionQueue.pendingFlow.value.single().path)
        val cached = repository.observeTracks(7).first().first { it.id == 101 }
        assertEquals("FLAC", cached.trackFile?.quality?.quality?.name)
        assertEquals("GRP", cached.trackFile?.releaseGroup)
    }

    @Test
    fun `track file delete offline queues and detaches from tracks`() = runTest {
        repository.refreshTracks(7)
        library.error = { op -> if (op == "deleteTrackFile") IOException("down") else null }

        val outcome = repository.deleteTrackFile(301)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("DELETE", actionQueue.pendingFlow.value.single().method)
        val cached = repository.observeTracks(7).first().first { it.id == 101 }
        assertFalse(cached.hasFile)
        assertEquals(0, cached.trackFileId)
    }

    @Test
    fun `delete artist syncs and purges cache`() = runTest {
        repository.refreshArtist(7)
        repository.refreshAlbums(7)
        repository.refreshTracks(7)

        val outcome = repository.deleteArtist(7, deleteFiles = true, addImportListExclusion = false)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertNull(repository.cachedArtist(7))
        assertTrue(repository.observeAlbums(7).first().isEmpty())
        assertTrue(repository.observeTracks(7).first().isEmpty())
        assertNull(library.artists[7])
    }

    @Test
    fun `delete artist offline queues with flags in path`() = runTest {
        library.error = { op -> if (op == "deleteArtist") IOException("down") else null }

        val outcome = repository.deleteArtist(7, deleteFiles = true, addImportListExclusion = true)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = actionQueue.pendingFlow.value.single()
        assertEquals("DELETE", action.method)
        assertEquals("api/v1/artist/7?deleteFiles=true&addImportListExclusion=true", action.path)
        assertNull(repository.cachedArtist(7))
    }

    @Test
    fun `history refresh caches and reconciles dropped rows`() = runTest {
        queue.history[1] = history(1, trackId = 101)
        queue.history[2] = history(2, trackId = 102)

        repository.refreshHistory(7)
        assertEquals(2, repository.observeHistory(7).first().size)

        queue.history.remove(2)
        repository.refreshHistory(7)
        assertEquals(listOf(1), repository.observeHistory(7).first().map { it.id })
    }

    @Test
    fun `rename and retag previews return rows`() = runTest {
        val renames = repository.renamePreview(7, albumId = 50)
        val paths = (renames as ArrResult.Ok).value.map { it.newPath }
        assertEquals(listOf("/music/01 - Track.mp3"), paths)

        val retags = repository.retagPreview(7, albumId = null)
        val rows = (retags as ArrResult.Ok).value
        assertEquals(301, rows.single().trackFileId)
        assertEquals("title", rows.single().changes.orEmpty().single().field)
    }

    @Test
    fun `command failure queues and auth failure errors`() = runTest {
        posterResult = ArrResult.err(ArrError.Network(null))
        val queued = repository.runCommandBody(
            LidarrCommandBodies.albumSearch(50),
            "Search album",
        )
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/command", actionQueue.pendingFlow.value.single().path)
        assertTrue(
            actionQueue.pendingFlow.value.single().bodyJson.orEmpty().contains("AlbumSearch"),
        )

        posterResult = ArrResult.err(ArrError.Auth(401))
        val rejected = repository.runCommandBody(
            LidarrCommandBodies.artistSearch(7),
            "Search artist",
        )
        assertTrue(rejected is ArrResult.Err)
    }

    @Test
    fun `grab release offline queues`() = runTest {
        queue.error = { op -> if (op == "grabRelease") IOException("down") else null }

        val outcome = repository.grabRelease(
            ReleaseResource(guid = "g1", artistId = 7, albumId = 50, title = "Belle - Studio"),
        )

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/release", actionQueue.pendingFlow.value.single().path)
    }

    @Test
    fun `interactive import offline queues payload`() = runTest {
        queue.manualImports[9] = ManualImportResource(
            id = 9,
            path = "/music/candidate.mp3",
            name = "candidate.mp3",
            artist = ArtistResource(id = 7),
            album = AlbumResource(id = 50),
        )
        queue.error = { op -> if (op == "updateManualImport") IOException("down") else null }
        val payload = dev.appolon.homelab.arr.api.lidarr.model.ManualImportUpdateResource(
            id = 9,
            path = "/music/candidate.mp3",
            artistId = 7,
            albumId = 50,
        )

        val outcome = repository.importItems(listOf(payload))

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/manualimport", actionQueue.pendingFlow.value.single().path)
        assertTrue(
            actionQueue.pendingFlow.value.single().bodyJson
                .orEmpty()
                .contains("/music/candidate.mp3"),
        )
    }

    @Test
    fun `lookups cache within ttl`() = runTest {
        repository.qualityProfiles()
        repository.qualityProfiles()
        assertEquals(1, profileFetches)

        val qualities = repository.qualityDefinitions() as ArrResult.Ok
        assertEquals(2, qualities.value.size)
    }

    @Test
    fun `queued badge maps command and album actions to this artist`() = runTest {
        repository.refreshAlbums(7)
        library.error = { op -> if (op == "updateArtist") IOException("down") else null }
        repository.setArtistMonitored(artist(), monitored = false)
        posterResult = ArrResult.err(ArrError.Network(null))
        repository.runCommandBody(LidarrCommandBodies.albumSearch(50), "Search album")

        val ids = repository.queuedArtistIds().first()

        assertEquals(setOf(7), ids)
        assertEquals(2, repository.queuedCount().first())
    }

    private fun artist() = ArtistResource(
        id = 7,
        artistName = "Belle",
        monitored = true,
        qualityProfileId = 1,
        metadataProfileId = 1,
        path = "/music/Belle",
        genres = listOf("Pop"),
        tags = listOf(4),
    )

    private fun album(id: Int, title: String, type: String, monitored: Boolean) = AlbumResource(
        id = id,
        title = title,
        artistId = 7,
        albumType = type,
        monitored = monitored,
        profileId = 1,
        releaseDate = "2024-0$id-01T00:00:00Z",
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
        eventType = EntityHistoryEventType.GRABBED,
        date = "2026-10-0${id}T00:00:00Z",
        quality = QualityModel(quality = Quality(id = 1, name = "MP3-320")),
    )
}
