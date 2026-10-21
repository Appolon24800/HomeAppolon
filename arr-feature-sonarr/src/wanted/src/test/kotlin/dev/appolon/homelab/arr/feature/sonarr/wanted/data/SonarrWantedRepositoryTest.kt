package dev.appolon.homelab.arr.feature.sonarr.wanted.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrDiscovery
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.wanted.FakeWantedActionQueue
import dev.appolon.homelab.arr.feature.sonarr.wanted.FakeWantedEntityStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SonarrWantedRepositoryTest {

    private val serverId = ServerId("s1")
    private val io = UnconfinedTestDispatcher()
    private lateinit var fake: FakeSonarrDiscovery
    private lateinit var store: FakeWantedEntityStore
    private lateinit var queue: FakeWantedActionQueue
    private lateinit var repository: SonarrWantedRepository

    private val commandCalls = mutableListOf<Triple<String, Int?, List<Int>?>>()
    private var commandResult: ArrResult<Unit> = ArrResult.ok(Unit)

    @Before
    fun setUp() {
        fake = FakeSonarrDiscovery()
        store = FakeWantedEntityStore()
        queue = FakeWantedActionQueue()
        commandCalls.clear()
        repository = newRepository()
        repeat(120) { index ->
            fake.missingEpisodes += episode(index + 1)
        }
    }

    private fun episode(
        id: Int,
        seriesId: Int = (id - 1) / 10 + 1,
        season: Int = (id - 1) % 2 + 1,
    ) = EpisodeResource(
        id = id,
        seriesId = seriesId,
        seasonNumber = season,
        episodeNumber = id,
        title = "Episode $id",
        airDateUtc = "2026-01-%02dT00:00:00Z".format(id % 28 + 1),
        monitored = true,
        series = SeriesResource(
            id = seriesId,
            title = "Series %02d".format(seriesId),
            sortTitle = "series %02d".format(seriesId),
        ),
    )

    private fun newRepository(
        missingApi: dev.appolon.homelab.arr.api.sonarr.MissingApi = fake.missingApi,
        cutoffApi: dev.appolon.homelab.arr.api.sonarr.CutoffApi = fake.cutoff,
    ) = SonarrWantedRepository(
        serverId = serverId,
        store = store,
        missingApi = missingApi,
        cutoffApi = cutoffApi,
        commandRunner = { name, seriesId, episodeIds ->
            commandCalls += Triple(name, seriesId, episodeIds)
            commandResult
        },
        actionQueue = queue,
        now = { 1_000L },
        io = io,
    )

    @Test
    fun `refresh centers the cache on the first pages`() = runTest {
        val result = repository.refresh(WantedTab.MISSING)
        val meta = (result as ArrResult.Ok).value
        assertEquals(100, repository.cached(WantedTab.MISSING).size)
        assertEquals(120, meta.totalRecords)
        assertEquals(2, meta.pagesLoaded)
        assertEquals(1_000L, store.lastFetchedAt(serverId, WantedTab.MISSING.entityType))
    }

    @Test
    fun `refresh drops rows the server no longer lists`() = runTest {
        repository.refresh(WantedTab.MISSING)
        fake.missingEpisodes.clear()
        repository.refresh(WantedTab.MISSING)
        assertTrue(repository.cached(WantedTab.MISSING).isEmpty())
    }

    @Test
    fun `loadNextPage appends until the server total is cached`() = runTest {
        repository.refresh(WantedTab.MISSING)
        val appended = repository.loadNextPage(WantedTab.MISSING) as ArrResult.Ok
        assertEquals(20, appended.value.appended)
        assertTrue(appended.value.endReached)
        assertEquals(120, repository.cached(WantedTab.MISSING).size)
        val again = repository.loadNextPage(WantedTab.MISSING) as ArrResult.Ok
        assertEquals(0, again.value.appended)
        assertTrue(again.value.endReached)
    }

    @Test
    fun `loadNextPage keeps the refresh stamp`() = runTest {
        repository.refresh(WantedTab.MISSING)
        repository.loadNextPage(WantedTab.MISSING)
        assertEquals(1_000L, store.lastFetchedAt(serverId, WantedTab.MISSING.entityType))
    }

    @Test
    fun `tabs cache independently of each other`() = runTest {
        fake.cutoffEpisodes += episode(501)
        repository.refresh(WantedTab.MISSING)
        repository.refresh(WantedTab.CUTOFF)
        assertTrue(
            store.get(
                serverId,
                WantedTab.MISSING.entityType,
                "1",
                EpisodeResource.serializer(),
            ) != null,
        )
        assertTrue(
            store.get(
                serverId,
                WantedTab.CUTOFF.entityType,
                "501",
                EpisodeResource.serializer(),
            ) != null,
        )
    }

    @Test
    fun `isStale honors the ttl`() = runTest {
        assertTrue(repository.isStale(WantedTab.MISSING))
        repository.refresh(WantedTab.MISSING)
        assertTrue(!repository.isStale(WantedTab.MISSING))
        val aged = SonarrWantedRepository(
            serverId = serverId,
            store = store,
            missingApi = fake.missingApi,
            cutoffApi = fake.cutoff,
            commandRunner = { _, _, _ -> ArrResult.ok(Unit) },
            actionQueue = queue,
            now = { 1_000L + SonarrWantedRepository.WANTED_TTL_MILLIS + 1 },
            io = io,
        )
        assertTrue(aged.isStale(WantedTab.MISSING))
    }

    @Test
    fun `http failure surfaces as error and keeps the cache`() = runTest {
        repository.refresh(WantedTab.MISSING)
        fake.error = { operation ->
            if (operation == "Missing.list") {
                HttpException(Response.error<Any>(500, "{}".toResponseBody()))
            } else {
                null
            }
        }
        val result = repository.refresh(WantedTab.MISSING)
        val error = (result as ArrResult.Err).error
        assertTrue(error is ArrError.Http)
        assertEquals(100, repository.cached(WantedTab.MISSING).size)
    }

    @Test
    fun `episode search dispatches EpisodeSearch with the ids`() = runTest {
        val outcome = repository.searchEpisodes(listOf(7, 9))
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf(Triple("EpisodeSearch", null, listOf(7, 9))), commandCalls)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `episode search queues for replay when offline`() = runTest {
        commandResult = ArrResult.err(ArrError.Offline())
        val outcome = repository.searchEpisodes(listOf(7))
        val queued = (outcome as ArrResult.Ok).value
        assertTrue(queued is WriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v3/command", action.path)
        assertTrue(action.bodyJson!!.contains("EpisodeSearch"))
        assertTrue(action.bodyJson!!.contains("7"))
        assertEquals(setOf(7), repository.queuedSearchEpisodeIds().first())
    }

    @Test
    fun `episode search fails outright on auth errors`() = runTest {
        commandResult = ArrResult.err(ArrError.Auth(401))
        val outcome = repository.searchEpisodes(listOf(7))
        assertTrue(outcome is ArrResult.Err)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `network throw queues like offline`() = runTest {
        commandResult = ArrResult.err(ArrError.Network(IOException()))
        val outcome = repository.searchEpisodes(listOf(7, 8))
        assertTrue(((outcome as ArrResult.Ok).value) is WriteOutcome.Queued)
        assertEquals(setOf(7, 8), repository.queuedSearchEpisodeIds().first())
    }

    @Test
    fun `series search dispatches SeriesSearch and queues offline`() = runTest {
        val outcome = repository.searchSeries(4)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf(Triple("SeriesSearch", 4, null)), commandCalls)

        commandResult = ArrResult.err(ArrError.Offline())
        val queued = repository.searchSeries(4) as ArrResult.Ok
        assertTrue(queued.value is WriteOutcome.Queued)
        assertTrue(queue.pending().last().bodyJson!!.contains("SeriesSearch"))
        assertEquals(setOf(4), repository.queuedSearchSeriesIds().first())
    }

    @Test
    fun `empty search dispatch is a no-op`() = runTest {
        val outcome = repository.searchEpisodes(emptyList())
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(commandCalls.isEmpty())
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `counts merge both tabs as they refresh`() = runTest {
        fake.missingEpisodes.clear()
        fake.missingEpisodes += episode(1)
        fake.cutoffEpisodes.clear()
        repeat(5) { index -> fake.cutoffEpisodes += episode(index + 1) }
        assertEquals(WantedCounts(), repository.observeCounts().first())
        repository.refresh(WantedTab.MISSING)
        assertEquals(WantedCounts(missing = 1), repository.observeCounts().first())
        repository.refresh(WantedTab.CUTOFF)
        assertEquals(WantedCounts(missing = 1, cutoff = 5), repository.observeCounts().first())
    }
}
