package dev.appolon.homelab.arr.feature.radarr.search.data

import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityModel
import dev.appolon.homelab.arr.api.radarr.model.QualitySource
import dev.appolon.homelab.arr.api.radarr.model.Revision
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.search.FakeRadarrReleases
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
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

class RadarrReleaseRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeRadarrReleases
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private var nowMillis = 1_000L

    private val repository: RadarrReleaseRepository
        get() = RadarrReleaseRepository(
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
        score: Int = 0,
        seeders: Int? = null,
        movieId: Int? = 5,
        qualityId: Int = 7,
    ) = RadarrReleaseResource(
        guid = guid,
        title = "Movie 2020 $guid",
        movieId = movieId,
        indexer = "indexer-a",
        size = 2_000_000_000,
        seeders = seeders,
        leechers = seeders?.let { it / 2 },
        protocol = if (seeders == null) DownloadProtocol.USENET else DownloadProtocol.TORRENT,
        customFormatScore = score,
        customFormats = listOf(CustomFormatResource(id = 1, name = "HQ")),
        quality = QualityModel(
            quality = Quality(id = qualityId, name = "WEBDL-1080p", source = QualitySource.WEBDL),
            revision = Revision(version = 1),
        ),
        languages = listOf(Language(id = 1, name = "English")),
        rejections = listOf("Not a favorite"),
    )

    @Before
    fun setUp() {
        fake = FakeRadarrReleases()
        store = FakeEntityStore()
        store.clock = { nowMillis }
        actionQueue = ActionQueueImpl(store)
    }

    @Test
    fun `refresh fetches caches and a second load inside the TTL skips the network`() = runTest {
        fake.releases += release("g1")

        val first = repository.releases(5)
        val firstCalls = fake.listCalls
        val second = repository.releases(5)

        assertTrue(first is ArrResult.Ok && (first as ArrResult.Ok).value.size == 1)
        assertTrue(second is ArrResult.Ok)
        assertEquals(firstCalls, fake.listCalls)
        assertEquals(listOf(5), fake.searchedMovieIds)
    }

    @Test
    fun `isStale turns true once the TTL passed`() = runTest {
        fake.releases += release("g1")
        repository.refresh(5)
        assertFalse(repository.isStale(5))

        nowMillis += RadarrReleaseRepository.RELEASE_TTL_MILLIS + 1

        assertTrue(repository.isStale(5))
    }

    @Test
    fun `releases reports a failed refresh and preserves the cache`() = runTest {
        fake.releases += release("g1")
        repository.refresh(5)
        fake.errorFor = { op -> if (op == "list") IOException("offline") else null }

        nowMillis += RadarrReleaseRepository.RELEASE_TTL_MILLIS + 1
        val result = repository.releases(5)

        assertTrue(result is ArrResult.Err)
        assertEquals(1, repository.cachedReleases(5).size)
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `releases reports the error when nothing is cached`() = runTest {
        fake.errorFor = { op -> if (op == "list") IOException("offline") else null }

        val result = repository.releases(5)

        assertTrue(result is ArrResult.Err)
    }

    @Test
    fun `grabRelease posts the release with overrides and the movie id`() = runTest {
        val quality = Quality(id = 9, name = "Bluray-1080p")
        val language = Language(id = 3, name = "German")

        val outcome = repository.grabRelease(5, release("g1"), quality, language)

        val ok = outcome as ArrResult.Ok
        assertEquals(WriteOutcome.Synced, ok.value)
        val body = fake.grabs.single()
        assertEquals(9, body.quality.quality?.id)
        assertEquals(1, body.quality.revision?.version)
        assertEquals(listOf(3), body.languages?.map { it.id })
        assertEquals(5, body.movieId)
    }

    @Test
    fun `grabRelease keeps parsed values without overrides`() = runTest {
        repository.grabRelease(5, release("g1"), null, null)

        val body = fake.grabs.single()
        assertEquals(7, body.quality.quality?.id)
        assertEquals(listOf(1), body.languages?.map { it.id })
    }

    @Test
    fun `grabRelease offline enqueues the POST for replay`() = runTest {
        fake.errorFor = { op -> if (op == "grab") IOException("offline") else null }

        val outcome = repository.grabRelease(5, release("g1"), null, null)

        val ok = outcome as ArrResult.Ok
        assertTrue(ok.value is WriteOutcome.Queued)
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v3/release", pending.path)
        assertTrue(pending.bodyJson!!.contains("\"movieId\":5"))
    }

    @Test
    fun `grabRelease does not queue terminal failures`() = runTest {
        fake.errorFor = { op ->
            if (op == "grab") {
                HttpException(Response.error<Unit>(401, "{}".toResponseBody()))
            } else {
                null
            }
        }

        val outcome = repository.grabRelease(5, release("g1"), null, null)

        assertTrue(outcome is ArrResult.Err)
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `pushRelease posts online and enqueues offline`() = runTest {
        val pushed = release("g9", movieId = null)

        val online = repository.pushRelease(pushed)
        fake.errorFor = { op -> if (op == "push") IOException("offline") else null }
        val offline = repository.pushRelease(pushed)

        assertTrue(online is ArrResult.Ok && (online as ArrResult.Ok).value == WriteOutcome.Synced)
        assertTrue(
            offline is ArrResult.Ok && (offline as ArrResult.Ok).value is WriteOutcome.Queued,
        )
        assertEquals(1, fake.pushes.size)
        val pending = actionQueue.pending().single()
        assertEquals("api/v3/release/push", pending.path)
    }

    @Test
    fun `queuedMovieIds exposes movies with pending grabs and pushes`() = runTest {
        fake.errorFor = { op ->
            if (op == "grab" || op == "push") IOException("offline") else null
        }
        repository.grabRelease(5, release("g1"), null, null)
        repository.pushRelease(release("g9", movieId = 6))

        val queued = repository.queuedMovieIds().first()

        assertEquals(setOf(5, 6), queued)
    }
}
