package dev.appolon.homelab.arr.feature.radarr.wanted.data

import dev.appolon.homelab.arr.api.radarr.FakeRadarrDiscovery
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieCommandRunner
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.wanted.FakeWantedActionQueue
import dev.appolon.homelab.arr.feature.radarr.wanted.FakeWantedEntityStore
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

class RadarrWantedRepositoryTest {

    private val serverId = ServerId("s1")
    private val io = UnconfinedTestDispatcher()
    private lateinit var fake: FakeRadarrDiscovery
    private lateinit var store: FakeWantedEntityStore
    private lateinit var queue: FakeWantedActionQueue
    private lateinit var repository: RadarrWantedRepository

    private val commandCalls = mutableListOf<Pair<String, List<Int>?>>()
    private var commandResult: ArrResult<Unit> = ArrResult.ok(Unit)

    @Before
    fun setUp() {
        fake = FakeRadarrDiscovery()
        store = FakeWantedEntityStore()
        queue = FakeWantedActionQueue()
        commandCalls.clear()
        repository = newRepository(commandRunner = { command, ids ->
            commandCalls += command to ids
            commandResult
        })
        repeat(120) { index ->
            fake.movies += MovieResource(
                id = index + 1,
                title = "Title %03d".format(index + 1),
                sortTitle = "title %03d".format(index + 1),
                monitored = true,
                qualityProfileId = 1,
            )
        }
    }

    private fun newRepository(
        commandRunner: MovieCommandRunner,
        missingApi: dev.appolon.homelab.arr.api.radarr.MissingApi = fake,
        cutoffApi: dev.appolon.homelab.arr.api.radarr.CutoffApi = fake,
    ) = RadarrWantedRepository(
        serverId = serverId,
        store = store,
        missingApi = missingApi,
        cutoffApi = cutoffApi,
        qualityProfileApi = {
            listOf(QualityProfileResource(id = 1, name = "HD"))
        },
        commandRunner = commandRunner,
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
        fake.movies.replaceAll { it.copy(hasFile = true) }
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
        repository.refresh(WantedTab.MISSING)
        repository.refresh(WantedTab.CUTOFF)
        assertTrue(
            store.get(
                serverId,
                WantedTab.MISSING.entityType,
                "1",
                MovieResource.serializer(),
            ) != null,
        )
        assertTrue(
            store.get(
                serverId,
                WantedTab.CUTOFF.entityType,
                "1",
                MovieResource.serializer(),
            ) != null,
        )
    }

    @Test
    fun `isStale honors the ttl`() = runTest {
        assertTrue(repository.isStale(WantedTab.MISSING))
        repository.refresh(WantedTab.MISSING)
        assertTrue(!repository.isStale(WantedTab.MISSING))
        val aged = RadarrWantedRepository(
            serverId = serverId,
            store = store,
            missingApi = fake,
            cutoffApi = fake,
            qualityProfileApi = { emptyList() },
            commandRunner = { _, _ -> ArrResult.ok(Unit) },
            actionQueue = queue,
            now = { 1_000L + RadarrWantedRepository.WANTED_TTL_MILLIS + 1 },
            io = io,
        )
        assertTrue(aged.isStale(WantedTab.MISSING))
    }

    @Test
    fun `http failure surfaces as error and keeps the cache`() = runTest {
        repository.refresh(WantedTab.MISSING)
        // The fake funnels missing() through cutoff(), so the operation name
        // it reports for both wanted endpoints is "cutoff".
        fake.errorFor = { operation ->
            if (operation == "cutoff") {
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
    fun `search dispatches MoviesSearch with the ids`() = runTest {
        val outcome = repository.searchMovies(listOf(7, 9))
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf("MoviesSearch" to listOf(7, 9)), commandCalls)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `search queues for replay when offline`() = runTest {
        commandResult = ArrResult.err(ArrError.Offline())
        val outcome = repository.searchMovies(listOf(7))
        val queued = (outcome as ArrResult.Ok).value
        assertTrue(queued is WriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v3/command", action.path)
        assertTrue(action.bodyJson!!.contains("MoviesSearch"))
        assertTrue(action.bodyJson!!.contains("7"))
        assertEquals(setOf(7), repository.queuedSearchMovieIds().first())
    }

    @Test
    fun `search fails outright on auth errors`() = runTest {
        commandResult = ArrResult.err(ArrError.Auth(401))
        val outcome = repository.searchMovies(listOf(7))
        assertTrue(outcome is ArrResult.Err)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `network throw queues like offline`() = runTest {
        commandResult = ArrResult.err(ArrError.Network(IOException()))
        val outcome = repository.searchMovies(listOf(7, 8))
        assertTrue(((outcome as ArrResult.Ok).value) is WriteOutcome.Queued)
        assertEquals(setOf(7, 8), repository.queuedSearchMovieIds().first())
    }

    @Test
    fun `counts merge both tabs as they refresh`() = runTest {
        val missingFake = FakeRadarrDiscovery()
        missingFake.movies += MovieResource(id = 1, title = "A")
        val cutoffFake = FakeRadarrDiscovery()
        repeat(5) { index ->
            cutoffFake.movies += MovieResource(id = index + 1, title = "B$index")
        }
        val mixed = newRepository(
            commandRunner = { _, _ -> ArrResult.ok(Unit) },
            missingApi = missingFake,
            cutoffApi = cutoffFake,
        )
        assertEquals(WantedCounts(), mixed.observeCounts().first())
        mixed.refresh(WantedTab.MISSING)
        assertEquals(WantedCounts(missing = 1), mixed.observeCounts().first())
        mixed.refresh(WantedTab.CUTOFF)
        assertEquals(WantedCounts(missing = 1, cutoff = 5), mixed.observeCounts().first())
    }

    @Test
    fun `quality profiles cache with their own ttl`() = runTest {
        val first = repository.qualityProfiles() as ArrResult.Ok
        assertEquals(1, first.value.single().id)
        val cached = repository.qualityProfiles() as ArrResult.Ok
        assertEquals(first.value, cached.value)
    }
}
