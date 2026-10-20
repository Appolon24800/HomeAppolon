package dev.appolon.homelab.arr.feature.sonarr.search.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityModel
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.search.FakeSearchEntityStore
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

class SonarrReleaseRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeSonarrQueue
    private lateinit var store: FakeSearchEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private var nowMillis = 1_000L
    private var listCalls = 0

    private val repository: SonarrReleaseRepository
        get() = SonarrReleaseRepository(
            serverId = serverId,
            store = store,
            releaseApi = fake.release,
            releasePushApi = fake.releasePush,
            actionQueue = actionQueue,
            now = { nowMillis },
            io = Dispatchers.Unconfined,
        )

    private fun release(
        guid: String,
        seriesId: Int? = 5,
        episodeId: Int? = null,
        episodeIds: List<Int>? = null,
        seasonNumber: Int = 2,
        score: Int = 0,
        seeders: Int? = null,
        qualityId: Int = 7,
    ) = ReleaseResource(
        guid = guid,
        title = "Series S02E05 $guid",
        seriesId = seriesId,
        episodeId = episodeId,
        episodeIds = episodeIds,
        seasonNumber = seasonNumber,
        indexer = "indexer-a",
        size = 2_000_000_000,
        seeders = seeders,
        leechers = seeders?.let { it / 2 },
        protocol = if (seeders == null) DownloadProtocol.USENET else DownloadProtocol.TORRENT,
        customFormatScore = score,
        customFormats = listOf(CustomFormatResource(id = 1, name = "HQ")),
        quality = QualityModel(quality = Quality(id = qualityId, name = "WEBDL-1080p")),
        languages = listOf(Language(id = 1, name = "English")),
        rejections = listOf("Not a favorite"),
    )

    @Before
    fun setUp() {
        fake = FakeSonarrQueue()
        fake.latency = { operation -> if (operation == "Release.list") listCalls += 1 }
        store = FakeSearchEntityStore()
        store.clock = { nowMillis }
        actionQueue = ActionQueueImpl(store)
    }

    @Test
    fun `refresh fetches caches and a second load inside the TTL skips the network`() = runTest {
        fake.releases += release("g1")

        val scope = ReleaseScope.Season(seriesId = 5, seasonNumber = 2)
        val first = repository.releases(scope)
        val callsAfterFirst = listCalls
        val second = repository.releases(scope)

        assertTrue(first is ArrResult.Ok && (first as ArrResult.Ok).value.size == 1)
        assertTrue(second is ArrResult.Ok)
        assertEquals(callsAfterFirst, listCalls)
    }

    @Test
    fun `episode scope searches by episode id and keeps its own cache row`() = runTest {
        fake.releases += listOf(
            release("g-match", episodeIds = listOf(9)),
            release("g-other", episodeId = 10),
        )

        val scope = ReleaseScope.Episode(seriesId = 5, episodeId = 9)
        val result = repository.releases(scope)

        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("g-match"), (result as ArrResult.Ok).value.map { it.guid })
        // The season scope has no page of its own yet.
        assertTrue(
            repository.cachedReleases(ReleaseScope.Season(5, 2)).isEmpty(),
        )
    }

    @Test
    fun `isStale turns true once the TTL passed`() = runTest {
        fake.releases += release("g1")
        val scope = ReleaseScope.Series(5)
        repository.refresh(scope)
        assertFalse(repository.isStale(scope))

        nowMillis += SonarrReleaseRepository.RELEASE_TTL_MILLIS + 1

        assertTrue(repository.isStale(scope))
    }

    @Test
    fun `releases reports a failed refresh and preserves the cache`() = runTest {
        fake.releases += release("g1")
        val scope = ReleaseScope.Season(5, 2)
        repository.refresh(scope)
        fake.error = { operation ->
            if (operation == "Release.list") IOException("offline") else null
        }

        nowMillis += SonarrReleaseRepository.RELEASE_TTL_MILLIS + 1
        val result = repository.releases(scope)

        assertTrue(result is ArrResult.Err)
        assertEquals(1, repository.cachedReleases(scope).size)
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `releases reports the error when nothing is cached`() = runTest {
        fake.error = { operation ->
            if (operation == "Release.list") IOException("offline") else null
        }

        val result = repository.releases(ReleaseScope.Season(5, 2))

        assertTrue(result is ArrResult.Err)
    }

    @Test
    fun `grabRelease posts the release with overrides and the scope ids`() = runTest {
        val quality = Quality(id = 9, name = "Bluray-1080p")
        val language = Language(id = 3, name = "German")

        val outcome = repository.grabRelease(
            scope = ReleaseScope.Episode(seriesId = 5, episodeId = 9),
            release = release("g1"),
            qualityOverride = quality,
            languageOverride = language,
        )

        val ok = outcome as ArrResult.Ok
        assertEquals(WriteOutcome.Synced, ok.value)
        val body = fake.grabbedReleases.single()
        assertEquals(9, body.quality.quality.id)
        assertEquals(5, body.seriesId)
        assertEquals(9, body.episodeId)
        assertEquals(listOf(3), body.languages?.map { it.id })
    }

    @Test
    fun `grabRelease keeps parsed values without overrides`() = runTest {
        repository.grabRelease(
            scope = ReleaseScope.Season(seriesId = 5, seasonNumber = 2),
            release = release("g1"),
        )

        val body = fake.grabbedReleases.single()
        assertEquals(7, body.quality.quality.id)
        assertEquals(listOf(1), body.languages?.map { it.id })
    }

    @Test
    fun `grabRelease offline enqueues the POST for replay`() = runTest {
        fake.error = { operation ->
            if (operation == "Release.create") IOException("offline") else null
        }

        val outcome = repository.grabRelease(
            scope = ReleaseScope.Season(seriesId = 5, seasonNumber = 2),
            release = release("g1"),
        )

        val ok = outcome as ArrResult.Ok
        assertTrue(ok.value is WriteOutcome.Queued)
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v3/release", pending.path)
        assertTrue(pending.bodyJson!!.contains("\"seriesId\":5"))
    }

    @Test
    fun `grabRelease does not queue terminal failures`() = runTest {
        fake.error = { operation ->
            if (operation == "Release.create") {
                HttpException(Response.error<Unit>(401, "{}".toResponseBody()))
            } else {
                null
            }
        }

        val outcome = repository.grabRelease(
            scope = ReleaseScope.Series(5),
            release = release("g1"),
        )

        assertTrue(outcome is ArrResult.Err)
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `pushRelease posts online and enqueues offline`() = runTest {
        val pushed = release("g9", seriesId = null)

        val online = repository.pushRelease(pushed)
        fake.error = { operation ->
            if (operation == "ReleasePush.create") IOException("offline") else null
        }
        val offline = repository.pushRelease(pushed)

        assertTrue(online is ArrResult.Ok && (online as ArrResult.Ok).value == WriteOutcome.Synced)
        assertTrue(
            offline is ArrResult.Ok && (offline as ArrResult.Ok).value is WriteOutcome.Queued,
        )
        assertEquals(1, fake.pushedReleases.size)
        assertEquals("api/v3/release/push", actionQueue.pending().single().path)
    }

    @Test
    fun `queuedSeriesIds exposes series with pending grabs and pushes`() = runTest {
        fake.error = { operation ->
            if (operation == "Release.create" || operation == "ReleasePush.create") {
                IOException("offline")
            } else {
                null
            }
        }
        repository.grabRelease(ReleaseScope.Season(5, 2), release("g1"))
        repository.pushRelease(release("g9", seriesId = 6))

        val queued = repository.queuedSeriesIds().first()

        assertEquals(setOf(5, 6), queued)
    }
}
