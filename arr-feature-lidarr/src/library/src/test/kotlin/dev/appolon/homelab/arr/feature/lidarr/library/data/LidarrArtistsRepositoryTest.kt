package dev.appolon.homelab.arr.feature.lidarr.library.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrLibrary
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStatisticsResource
import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.ArtistEditorResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.NewItemMonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.FakeActionQueue
import dev.appolon.homelab.arr.feature.lidarr.library.FakeEntityStore
import java.io.IOException
import dev.appolon.homelab.arr.api.lidarr.ArtistApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class LidarrArtistsRepositoryTest {

    private val serverId = ServerId("s1")
    private lateinit var fake: FakeLidarrLibrary
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var repository: LidarrArtistsRepository

    private val commandCalls = mutableListOf<Pair<String, List<Int>?>>()
    private var commandResult: ArrResult<Unit> = ArrResult.ok(Unit)

    private var clock = 1_000L
    private var profiles = listOf(QualityProfileResource(id = 1, name = "Lossless"), QualityProfileResource(id = 2, name = "MP3"))
    @Before
    fun setUp() {
        fake = FakeLidarrLibrary()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        commandCalls.clear()
        repository = LidarrArtistsRepository(
            serverId = serverId,
            store = store,
            artistApi = fake,
            artistEditorApi = fake,
            albumApi = fake,
            albumStudioApi = fake,
            qualityProfileApi = { profiles },
            metadataProfileApi = {
                listOf(MetadataProfileResource(id = 1, name = "Standard"))
            },
            commandRunner = { command, ids ->
                commandCalls += command to ids
                commandResult
            },
            actionQueue = queue,
            now = { clock },
            ioContext = Dispatchers.Unconfined,
        )
        fake.artists.putAll(
            listOf(
                ArtistResource(id = 1, artistName = "Alpha", monitored = true, qualityProfileId = 1),
                ArtistResource(id = 2, artistName = "Beta", monitored = false, qualityProfileId = 2),
            ).associateBy { it.id },
        )
        fake.albums.putAll(
            listOf(
                album(11, artistId = 1, monitored = true, trackFileCount = 0),
                album(12, artistId = 1, monitored = false, trackFileCount = 1),
                album(21, artistId = 2, monitored = true, trackFileCount = 2),
            ).associateBy { it.id },
        )
    }

    private fun album(
        id: Int,
        artistId: Int,
        monitored: Boolean,
        trackFileCount: Int,
        releaseDate: String = "2020-01-01",
    ) = AlbumResource(
        id = id,
        artistId = artistId,
        title = "Album $id",
        monitored = monitored,
        releaseDate = releaseDate,
        statistics = AlbumStatisticsResource(trackFileCount = trackFileCount, trackCount = 10),
    )


    /** Seeds the album cache the way scoped fetches (wanted, detail) do. */
    private suspend fun seedAlbumCache() {
        store.putAll(
            serverId,
            LidarrArtistsRepository.ALBUM_TYPE,
            fake.albums.values.map { it.id.toString() to it },
            AlbumResource.serializer(),
            fetchedAtMillis = 1_000L,
        )
    }

    private fun repositoryWithResponses(fetch: suspend () -> List<ArtistResource>): LidarrArtistsRepository {
        val api = object : ArtistApi by fake {
            override suspend fun getArtists(mbId: String?): List<ArtistResource> = fetch()
        }
        return LidarrArtistsRepository(
            serverId = serverId, store = store,
            artistApi = api, artistEditorApi = fake, albumApi = fake, albumStudioApi = fake,
            qualityProfileApi = { profiles }, metadataProfileApi = { emptyList() },
            commandRunner = { _, _ -> ArrResult.ok(Unit) }, actionQueue = queue,
            ioContext = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `cancelling an active refresh lets the waiting refresh finish`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        var calls = 0
        val concurrent = repositoryWithResponses {
            if (++calls == 1) {
                firstStarted.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
            listOf(ArtistResource(id = 2))
        }
        val first = async(UnconfinedTestDispatcher(testScheduler)) { concurrent.refresh() }
        firstStarted.await()
        val waiting = async(UnconfinedTestDispatcher(testScheduler)) { concurrent.refresh() }
        runCurrent()
        first.cancelAndJoin()
        assertTrue(waiting.await() is ArrResult.Ok)
        assertEquals(listOf(2), concurrent.cachedArtists().map { it.id })
    }

    @Test
    fun `an older overlapping refresh cannot overwrite the newer library`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var calls = 0
        val concurrent = repositoryWithResponses {
            if (++calls == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                listOf(ArtistResource(id = 1))
            } else {
                listOf(ArtistResource(id = 2))
            }
        }
        val older = async(UnconfinedTestDispatcher(testScheduler)) { concurrent.refresh() }
        firstStarted.await()
        val newer = async(UnconfinedTestDispatcher(testScheduler)) { concurrent.refresh() }
        runCurrent()
        releaseFirst.complete(Unit)
        assertTrue(older.await() is ArrResult.Ok)
        assertTrue(newer.await() is ArrResult.Ok)
        assertEquals(listOf(2), concurrent.cachedArtists().map { it.id })
    }

    @Test
    fun `observers see a complete replacement without partial lists`() = runTest {
        repository.refresh()
        val snapshots = mutableListOf<List<Int>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeArtists().collect { rows -> snapshots += rows.map { it.id } }
        }
        fake.artists.clear()
        fake.artists[3] = ArtistResource(id = 3, artistName = "Replacement")
        repository.refresh()
        assertEquals(listOf(listOf(1, 2), listOf(3)), snapshots)
    }

    @Test
    fun `cancelled library request preserves the cache and propagates cancellation`() = runTest {
        repository.refresh()
        val previous = repository.cachedArtists()
        fake.error = { _ -> CancellationException("Refresh cancelled") }
        val result = runCatching { repository.refresh() }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(previous, repository.cachedArtists())
    }

    @Test
    fun `cancelled cache write preserves the library and propagates cancellation`() = runTest {
        repository.refresh()
        val previous = repository.cachedArtists()
        fake.artists.clear()
        fake.artists[3] = ArtistResource(id = 3, artistName = "Replacement")
        store.writeFailure = CancellationException("Refresh cancelled")
        val result = runCatching { repository.refresh() }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(previous, repository.cachedArtists())
    }

    @Test
    fun `failed cache refresh preserves the complete previous library`() = runTest {
        repository.refresh()
        val previous = repository.cachedArtists()
        fake.artists.remove(1)
        store.writeFailure = IOException("Disk full")
        val result = runCatching { repository.refresh() }
        assertEquals(previous, repository.cachedArtists())
        assertTrue("Storage failure should return an error", result.getOrNull() is ArrResult.Err)
    }

    @Test
    fun `refresh stamps existing rows with the fetch clock`() = runTest {
        repository.refresh()
        clock = 2_000L
        repository.refresh()
        assertEquals(2_000L, store.lastFetchedAt(serverId, LidarrArtistsRepository.ARTIST_TYPE))
        clock = 2_501L
        assertTrue(repository.isStale(ttlMillis = 500))
    }

    @Test
    fun `profile refresh removes deleted entries and accepts an empty response`() = runTest {
        repository.qualityProfiles()
        profiles = profiles.take(1)
        repository.qualityProfiles(ttlMillis = -1)
        assertEquals(profiles, store.getAll(serverId, LidarrArtistsRepository.QUALITY_PROFILE_TYPE, QualityProfileResource.serializer()))
        profiles = emptyList()
        assertEquals(emptyList<QualityProfileResource>(), (repository.qualityProfiles(ttlMillis = -1) as ArrResult.Ok).value)
        assertTrue(store.getAll(serverId, LidarrArtistsRepository.QUALITY_PROFILE_TYPE, QualityProfileResource.serializer()).isEmpty())
        profiles = listOf(QualityProfileResource(id = 9, name = "Unfetched"))
        assertEquals(emptyList<QualityProfileResource>(), (repository.qualityProfiles() as ArrResult.Ok).value)
    }

    @Test
    fun `refresh fills the artist cache and never requests the album list`() = runTest {
        val operations = mutableListOf<String>()
        fake.error = { operation -> operations += operation; null }
        val result = repository.refresh()
        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("Alpha", "Beta"), repository.cachedArtists().map { it.artistName })
        assertTrue(repository.cachedAlbums().isEmpty())
        assertEquals(1_000L, store.lastFetchedAt(serverId, LidarrArtistsRepository.ARTIST_TYPE))
        // The unfiltered album list is tens of megabytes on large libraries;
        // one string materialization OOM-killed the process.
        assertTrue("getAlbums" !in operations)
    }

    @Test
    fun `cached albums come from scoped fetches not the library refresh`() = runTest {
        repository.refresh()
        seedAlbumCache()
        assertEquals(3, repository.cachedAlbums().size)
    }

    @Test
    fun `refresh reconciles artist rows removed on the server`() = runTest {
        repository.refresh()
        fake.artists.remove(1)
        repository.refresh()
        assertEquals(listOf("Beta"), repository.cachedArtists().map { it.artistName })
    }



    @Test
    fun `isStale honors the ttl`() = runTest {
        assertTrue(repository.isStale())
        repository.refresh()
        assertTrue(!repository.isStale())
        val later = LidarrArtistsRepository(
            serverId = serverId,
            store = store,
            artistApi = fake,
            artistEditorApi = fake,
            albumApi = fake,
            albumStudioApi = fake,
            qualityProfileApi = { emptyList() },
            metadataProfileApi = { emptyList() },
            commandRunner = { _, _ -> ArrResult.ok(Unit) },
            actionQueue = queue,
            now = { 1_000L + LidarrArtistsRepository.ARTIST_TTL_MILLIS + 1 },
            ioContext = Dispatchers.Unconfined,
        )
        assertTrue(later.isStale())
    }

    @Test
    fun `edit applies on the server and updates the cache`() = runTest {
        repository.refresh()
        val editor = ArtistEditorResource(
            artistIds = listOf(2),
            monitored = true,
            monitorNewItems = NewItemMonitorTypes.NEW,
            tags = listOf(5),
            applyTags = ApplyTags.ADD,
        )
        val outcome = repository.editArtists(editor)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        val cached = repository.cachedArtists().first { it.id == 2 }
        assertEquals(true, cached.monitored)
        assertEquals(NewItemMonitorTypes.NEW, cached.monitorNewItems)
        assertEquals(listOf(5), cached.tags)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `edit offline applies optimistically and enqueues a replayable action`() = runTest {
        repository.refresh()
        fake.error = { _ -> IOException("plane mode") }
        val editor = ArtistEditorResource(
            artistIds = listOf(2),
            monitored = true,
            monitorNewItems = NewItemMonitorTypes.NONE,
        )
        val outcome = repository.editArtists(editor)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val optimistic = repository.cachedArtists().first { it.id == 2 }
        assertEquals(true, optimistic.monitored)
        assertEquals(NewItemMonitorTypes.NONE, optimistic.monitorNewItems)
        val action = queue.pending().single()
        assertEquals("PUT", action.method)
        assertEquals("api/v1/artist/editor", action.path)
        assertEquals(serverId, action.serverId)
        assertTrue(action.bodyJson!!.contains("\"monitored\":true"))
        assertEquals(setOf(2), repository.queuedArtistIds().first())
    }

    @Test
    fun `missing strategy also monitors missing albums and enqueues the studio call`() = runTest {
        repository.refresh()
        seedAlbumCache()
        fake.error = { _ -> IOException("offline") }
        val editor = ArtistEditorResource(
            artistIds = listOf(1),
            monitorNewItems = NewItemMonitorTypes.NONE,
        )
        val outcome = repository.editArtists(editor, missingStrategy = true)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val paths = queue.pending().map { it.method to it.path }
        assertTrue("PUT" to "api/v1/artist/editor" in paths)
        assertTrue("POST" to "api/v1/albumstudio" in paths)
        // Album 11 is monitored but fileless, so it becomes monitored in the optimistic cache.
        assertTrue(repository.cachedAlbums().first { it.id == 11 }.monitored)
        assertEquals(setOf(1), repository.queuedArtistIds().first())
    }

    @Test
    fun `missing strategy online sends the studio call and stays off the queue`() = runTest {
        repository.refresh()
        val outcome = repository.editArtists(
            ArtistEditorResource(artistIds = listOf(1), monitorNewItems = NewItemMonitorTypes.NONE),
            missingStrategy = true,
        )
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `server errors do not enqueue`() = runTest {
        repository.refresh()
        fake.error = { _ -> HttpException(Response.error<Any>(409, "conflict".toResponseBody(null))) }
        val outcome = repository.editArtists(ArtistEditorResource(artistIds = listOf(1), monitored = false))
        val error = (outcome as ArrResult.Err).error
        assertTrue(error is ArrError.Http)
        assertEquals(409, (error as ArrError.Http).code)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `delete offline removes rows optimistically and enqueues`() = runTest {
        repository.refresh()
        seedAlbumCache()
        fake.error = { _ -> IOException("offline") }
        val outcome = repository.deleteArtists(listOf(1), deleteFiles = true, addImportListExclusion = false)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(listOf("Beta"), repository.cachedArtists().map { it.artistName })
        assertEquals(listOf(21), repository.cachedAlbums().map { it.id })
        val action = queue.pending().single()
        assertEquals("DELETE", action.method)
        assertTrue(action.bodyJson!!.contains("\"deleteFiles\":true"))
    }

    @Test
    fun `delete online removes rows and stays off the queue`() = runTest {
        repository.refresh()
        val outcome = repository.deleteArtists(listOf(1), deleteFiles = false, addImportListExclusion = true)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf("Beta"), repository.cachedArtists().map { it.artistName })
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `commands dispatch with selection ids`() = runTest {
        val outcome = repository.runCommand(ArtistCommand.REFRESH_METADATA, listOf(7, 8))
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf("RefreshArtist" to listOf(7, 8)), commandCalls)
    }

    @Test
    fun `commands offline queue with the artist ids in the body`() = runTest {
        commandResult = ArrResult.err(ArrError.Network(null))
        val outcome = repository.runCommand(ArtistCommand.SCAN_DISK, listOf(7))
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v1/command", action.path)
        assertEquals("{\"name\":\"RescanArtist\",\"artistIds\":[7]}", action.bodyJson)
    }

    @Test
    fun `quality and metadata profiles cache under their own entity types`() = runTest {
        val quality = repository.qualityProfiles()
        val metadata = repository.metadataProfiles()
        assertEquals(listOf("Lossless", "MP3"), (quality as ArrResult.Ok).value.map { it.name })
        assertEquals(listOf("Standard"), (metadata as ArrResult.Ok).value.map { it.name })
        val offline = LidarrArtistsRepository(
            serverId = serverId,
            store = store,
            artistApi = fake,
            artistEditorApi = fake,
            albumApi = fake,
            albumStudioApi = fake,
            qualityProfileApi = { throw IOException("offline") },
            metadataProfileApi = { throw IOException("offline") },
            commandRunner = { _, _ -> ArrResult.ok(Unit) },
            actionQueue = queue,
            now = { 1_500L },
            ioContext = Dispatchers.Unconfined,
        )
        val cachedQuality = offline.qualityProfiles()
        val cachedMetadata = offline.metadataProfiles()
        assertEquals(listOf("Lossless", "MP3"), (cachedQuality as ArrResult.Ok).value.map { it.name })
        assertEquals(listOf("Standard"), (cachedMetadata as ArrResult.Ok).value.map { it.name })
    }

    @Test
    fun `queuedArtistIds ignores other servers and unrelated paths`() = runTest {
        queue.enqueue(queue.action("a1").copy(serverId = ServerId("other")))
        queue.enqueue(
            queue.action("a2").copy(
                path = "api/v1/command",
                bodyJson = "{\"name\":\"RefreshArtist\",\"artistIds\":[4]}",
            ),
        )
        assertEquals(2, queue.pending().size)
        assertEquals(emptySet<Int>(), repository.queuedArtistIds().first())
    }
}
