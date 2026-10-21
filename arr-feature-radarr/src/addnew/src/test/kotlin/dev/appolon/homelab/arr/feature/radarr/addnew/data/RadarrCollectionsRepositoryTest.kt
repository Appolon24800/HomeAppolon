package dev.appolon.homelab.arr.feature.radarr.addnew.data

import dev.appolon.homelab.arr.api.radarr.FakeRadarrDiscovery
import dev.appolon.homelab.arr.api.radarr.fake.FakeRadarrLibrary
import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.MovieCollectionResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.addnew.FakeActionQueue
import dev.appolon.homelab.arr.feature.radarr.addnew.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadarrCollectionsRepositoryTest {

    private val serverId = ServerId("s1")
    private val io = UnconfinedTestDispatcher()
    private lateinit var library: FakeRadarrLibrary
    private lateinit var discovery: FakeRadarrDiscovery
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var commands: MutableList<Pair<String, List<Int>?>>
    private var commandResult: ArrResult<Unit> = ArrResult.ok(Unit)
    private var now: Long = 1_000L
    private lateinit var repository: RadarrCollectionsRepository

    @Before
    fun setUp() {
        library = FakeRadarrLibrary()
        discovery = FakeRadarrDiscovery()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        commands = mutableListOf()
        repository = RadarrCollectionsRepository(
            serverId = serverId,
            store = store,
            collectionApi = discovery,
            movieApi = library.movie,
            commandRunner = { command, movieIds ->
                commands += command to movieIds
                commandResult
            },
            actionQueue = queue,
            now = { now },
            io = io,
        )
        discovery.collections += CollectionResource(
            id = 1,
            title = "Dune Collection",
            tmdbId = 10,
            monitored = false,
            qualityProfileId = 1,
            missingMovies = 2,
        )
        library.movies += listOf(
            MovieResource(
                id = 1,
                title = "Dune",
                tmdbId = 11,
                hasFile = false,
                collection = MovieCollectionResource(tmdbId = 10),
            ),
            MovieResource(
                id = 2,
                title = "Dune Part Two",
                tmdbId = 12,
                hasFile = true,
                collection = MovieCollectionResource(tmdbId = 10),
            ),
        )
    }

    @Test
    fun `refresh stores collections and reconciles removals`() = runTest {
        val result = repository.refresh()
        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("Dune Collection"), repository.cachedCollections().map { it.title })

        discovery.collections.clear()
        repository.refresh()
        assertTrue(repository.cachedCollections().isEmpty())
    }

    @Test
    fun `refreshIfStale skips fresh rows and refreshes after the ttl`() = runTest {
        repository.refresh()
        now += RadarrCollectionsRepository.COLLECTION_TTL_MILLIS - 1
        discovery.collections.clear()
        repository.refreshIfStale()
        assertEquals(1, repository.cachedCollections().size)

        now += 2
        repository.refreshIfStale()
        assertTrue(repository.cachedCollections().isEmpty())
    }

    @Test
    fun `updateCollection syncs and writes the response through`() = runTest {
        repository.refresh()
        val updated = repository.cachedCollections().single().copy(monitored = true)
        val outcome = repository.updateCollection(updated)
        assertTrue((outcome as ArrResult.Ok).value is AddOutcome.Synced)
        assertEquals(true, repository.cachedCollections().single().monitored)
        assertEquals(true, discovery.collections.single().monitored)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `updateCollection offline queues the PUT and applies optimistically`() = runTest {
        repository.refresh()
        discovery.errorFor = { if (it == "updateCollection") IOException("plane mode") else null }
        val updated = repository.cachedCollections().single().copy(monitored = true)
        val outcome = repository.updateCollection(updated)
        assertTrue((outcome as ArrResult.Ok).value is AddOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("PUT", action.method)
        assertEquals("api/v3/collection/1", action.path)
        assertTrue(action.bodyJson.orEmpty().contains("\"monitored\":true"))
        assertEquals(true, repository.cachedCollections().single().monitored)
        assertEquals(setOf(1), repository.queuedCollectionIds().first())
    }

    @Test
    fun `missingMovieIds resolves through the movie cache`() = runTest {
        assertEquals(listOf(1), repository.missingMovieIds(10).let { (it as ArrResult.Ok).value })
    }

    @Test
    fun `searchMissing dispatches MoviesSearch with the missing ids`() = runTest {
        val outcome = repository.searchMissing(listOf(1, 3))
        assertTrue((outcome as ArrResult.Ok).value is AddOutcome.Synced)
        assertEquals(listOf(AddNewLogic.COMMAND_MOVIES_SEARCH to listOf(1, 3)), commands)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `searchMissing offline queues a replayable command action`() = runTest {
        commandResult = ArrResult.err(ArrError.Network(IOException("offline")))
        val outcome = repository.searchMissing(listOf(1))
        assertTrue((outcome as ArrResult.Ok).value is AddOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v3/command", action.path)
        assertTrue(action.bodyJson.orEmpty().contains("\"name\":\"MoviesSearch\""))
        assertTrue(action.bodyJson.orEmpty().contains("\"movieIds\":[1]"))
    }

    @Test
    fun `searchMissing with no ids is a no-op`() = runTest {
        val outcome = repository.searchMissing(emptyList())
        assertTrue((outcome as ArrResult.Ok).value is AddOutcome.Synced)
        assertTrue(commands.isEmpty())
        assertTrue(queue.pending().isEmpty())
    }
}
