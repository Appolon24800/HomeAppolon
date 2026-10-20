package dev.appolon.homelab.arr.feature.lidarr.search.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityModel
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class LidarrReleaseRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeLidarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private var nowMillis = 1_000L
    private var listCalls = 0

    private val repository: LidarrReleaseRepository
        get() = LidarrReleaseRepository(
            serverId = serverId,
            store = store,
            releaseApi = fake,
            releasePushApi = fake,
            actionQueue = actionQueue,
            now = { nowMillis },
            io = Dispatchers.Unconfined,
        )

    private fun release(
        guid: String,
        artistId: Int? = 5,
        albumId: Int? = 9,
        discography: Boolean = false,
        score: Int = 0,
        seeders: Int? = null,
        qualityId: Int = 7,
    ) = ReleaseResource(
        guid = guid,
        title = "Artist - Album 2019 FLAC $guid",
        artistId = artistId,
        albumId = if (discography) null else albumId,
        discography = discography,
        indexer = "indexer-a",
        size = 400_000_000,
        seeders = seeders,
        leechers = seeders?.let { it / 2 },
        protocol = if (seeders == null) DownloadProtocol.USENET else DownloadProtocol.TORRENT,
        customFormatScore = score,
        customFormats = listOf(CustomFormatResource(id = 1, name = "Lossless")),
        quality = QualityModel(quality = Quality(id = qualityId, name = "FLAC")),
        rejections = listOf("Below profile cutoff"),
    )

    /** FakeLidarrQueue keys releases by id; the wire ids are all zero, so keys are positional. */
    private fun seed(vararg resources: ReleaseResource) {
        var key = fake.releases.keys.maxOrNull() ?: 0
        resources.forEach { fake.releases[++key] = it }
    }

    @Before
    fun setUp() {
        fake = FakeLidarrQueue()
        fake.latency = { operation -> if (operation == "getReleases") listCalls += 1 }
        store = FakeEntityStore()
        store.clock = { nowMillis }
        actionQueue = ActionQueueImpl(store)
    }

    @Test
    fun `refresh fetches caches and a second load inside the TTL skips the network`() = runTest {
        seed(release("g1"))

        val scope = ReleaseScope.Artist(artistId = 5)
        val first = repository.releases(scope)
        val callsAfterFirst = listCalls
        val second = repository.releases(scope)

        assertTrue(first is ArrResult.Ok && (first as ArrResult.Ok).value.size == 1)
        assertTrue(second is ArrResult.Ok)
        assertEquals(callsAfterFirst, listCalls)
    }

    @Test
    fun `album scope searches by album id and keeps its own cache row`() = runTest {
        seed(
            release("g-match"),
            release("g-discog", discography = true),
        )

        val scope = ReleaseScope.Album(albumId = 9)
        val result = repository.releases(scope)

        assertTrue(result is ArrResult.Ok)
        assertEquals(
            listOf("g-match"),
            (result as ArrResult.Ok).value.filter { it.albumId == 9 }.map { it.guid },
        )
        // The artist scope has no page of its own yet.
        assertTrue(repository.cachedReleases(ReleaseScope.Artist(5)).isEmpty())
    }

    @Test
    fun `isStale turns true once the TTL passed`() = runTest {
        seed(release("g1"))
        val scope = ReleaseScope.Artist(5)
        repository.refresh(scope)
        assertFalse(repository.isStale(scope))

        nowMillis += LidarrReleaseRepository.RELEASE_TTL_MILLIS + 1

        assertTrue(repository.isStale(scope))
    }

    @Test
    fun `releases serves the cache when the refresh fails`() = runTest {
        seed(release("g1"))
        val scope = ReleaseScope.Artist(5)
        repository.refresh(scope)
        fake.error = { operation ->
            if (operation == "getReleases") IOException("offline") else null
        }

        nowMillis += LidarrReleaseRepository.RELEASE_TTL_MILLIS + 1
        val result = repository.releases(scope)

        assertTrue(result is ArrResult.Ok && (result as ArrResult.Ok).value.size == 1)
    }

    @Test
    fun `releases reports the error when nothing is cached`() = runTest {
        fake.error = { operation ->
            if (operation == "getReleases") IOException("offline") else null
        }

        val result = repository.releases(ReleaseScope.Album(9))

        assertTrue(result is ArrResult.Err)
    }

    @Test
    fun `grabRelease posts the release with the override and the scope ids`() = runTest {
        val quality = Quality(id = 9, name = "MP3-320")

        val outcome = repository.grabRelease(
            scope = ReleaseScope.Album(albumId = 9),
            release = release("g1"),
            qualityOverride = quality,
        )

        val ok = outcome as ArrResult.Ok
        assertEquals(WriteOutcome.Synced, ok.value)
        val body = fake.grabbedReleases.single()
        assertEquals(9, body.quality?.quality?.id)
        assertEquals(9, body.albumId)
        assertEquals(5, body.artistId)
    }

    @Test
    fun `grabRelease on artist scope pins the artist id`() = runTest {
        repository.grabRelease(
            scope = ReleaseScope.Artist(artistId = 5),
            release = release("g1"),
        )

        val body = fake.grabbedReleases.single()
        assertEquals(5, body.artistId)
        assertEquals(9, body.albumId)
        assertEquals(7, body.quality?.quality?.id)
    }

    @Test
    fun `grabRelease offline enqueues the POST for replay`() = runTest {
        fake.error = { operation ->
            if (operation == "grabRelease") IOException("offline") else null
        }

        val outcome = repository.grabRelease(
            scope = ReleaseScope.Artist(5),
            release = release("g1"),
        )

        val ok = outcome as ArrResult.Ok
        assertTrue(ok.value is WriteOutcome.Queued)
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v1/release", pending.path)
        assertTrue(pending.bodyJson!!.contains("\"artistId\":5"))
    }

    @Test
    fun `grabRelease does not queue terminal failures`() = runTest {
        fake.error = { operation ->
            if (operation == "grabRelease") {
                HttpException(Response.error<Unit>(401, "{}".toResponseBody()))
            } else {
                null
            }
        }

        val outcome = repository.grabRelease(
            scope = ReleaseScope.Artist(5),
            release = release("g1"),
        )

        assertTrue(outcome is ArrResult.Err)
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `pushRelease posts online and enqueues offline`() = runTest {
        val pushed = release("g9", artistId = null, albumId = null)

        val online = repository.pushRelease(pushed)
        fake.error = { operation ->
            if (operation == "pushRelease") IOException("offline") else null
        }
        val offline = repository.pushRelease(pushed)

        assertTrue(online is ArrResult.Ok && (online as ArrResult.Ok).value == WriteOutcome.Synced)
        assertTrue(
            offline is ArrResult.Ok && (offline as ArrResult.Ok).value is WriteOutcome.Queued,
        )
        assertEquals(1, fake.pushedReleases.size)
        assertEquals("api/v1/release/push", actionQueue.pending().single().path)
    }

    @Test
    fun `queued ids expose artists and albums with pending writes`() = runTest {
        fake.error = { operation ->
            if (operation == "grabRelease" || operation == "pushRelease") {
                IOException("offline")
            } else {
                null
            }
        }
        repository.grabRelease(ReleaseScope.Album(9), release("g1"))
        repository.pushRelease(release("g9", artistId = 6, albumId = 8))

        val artists = repository.queuedArtistIds().first()
        val albums = repository.queuedAlbumIds().first()

        assertEquals(setOf(5, 6), artists)
        assertEquals(setOf(9, 8), albums)
    }
}
