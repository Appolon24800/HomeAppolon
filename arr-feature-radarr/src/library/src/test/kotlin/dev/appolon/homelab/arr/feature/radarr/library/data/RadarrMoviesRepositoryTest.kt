package dev.appolon.homelab.arr.feature.radarr.library.data

import dev.appolon.homelab.arr.api.radarr.fake.FakeRadarrLibrary
import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.MovieEditorResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.FakeActionQueue
import dev.appolon.homelab.arr.feature.radarr.library.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class RadarrMoviesRepositoryTest {

    private val serverId = ServerId("s1")
    private val io = UnconfinedTestDispatcher()
    private lateinit var fake: FakeRadarrLibrary
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var repository: RadarrMoviesRepository

    private var commandCalls = mutableListOf<Pair<String, List<Int>?>>()
    private var commandResult: ArrResult<Unit> = ArrResult.ok(Unit)

    @Before
    fun setUp() {
        fake = FakeRadarrLibrary()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        commandCalls = mutableListOf()
        repository = RadarrMoviesRepository(
            serverId = serverId,
            store = store,
            movieApi = fake.movie,
            movieEditorApi = fake.movieEditor,
            qualityProfileApi = {
                listOf(QualityProfileResource(id = 1, name = "HD"), QualityProfileResource(id = 2, name = "UHD"))
            },
            commandRunner = { command, ids ->
                commandCalls += command to ids
                commandResult
            },
            actionQueue = queue,
            now = { 1_000L },
            io = io,
        )
        fake.movies += listOf(
            MovieResource(id = 1, title = "Alpha", monitored = true, qualityProfileId = 1),
            MovieResource(id = 2, title = "Beta", monitored = false, qualityProfileId = 2),
        )
    }

    @Test
    fun `refresh fills the cache and stamps fetchedAt`() = runTest {
        val result = repository.refresh()
        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("Alpha", "Beta"), repository.cachedMovies().map { it.title })
        assertEquals(1_000L, store.lastFetchedAt(serverId, RadarrMoviesRepository.MOVIE_TYPE))
    }

    @Test
    fun `refresh reconciles rows removed on the server`() = runTest {
        repository.refresh()
        fake.movies.removeAll { it.id == 1 }
        repository.refresh()
        assertEquals(listOf("Beta"), repository.cachedMovies().map { it.title })
    }

    @Test
    fun `isStale honors the ttl`() = runTest {
        assertTrue(repository.isStale())
        repository.refresh()
        assertTrue(!repository.isStale())
        val later = RadarrMoviesRepository(
            serverId = serverId,
            store = store,
            movieApi = fake.movie,
            movieEditorApi = fake.movieEditor,
            qualityProfileApi = { emptyList() },
            commandRunner = { _, _ -> ArrResult.ok(Unit) },
            actionQueue = queue,
            now = { 1_000L + RadarrMoviesRepository.MOVIE_TTL_MILLIS + 1 },
            io = io,
        )
        assertTrue(later.isStale())
    }

    @Test
    fun `edit applies on the server and updates the cache`() = runTest {
        repository.refresh()
        val editor = MovieEditorResource(
            movieIds = listOf(2),
            monitored = true,
            tags = listOf(5),
            applyTags = ApplyTags.ADD,
        )
        val outcome = repository.editMovies(editor)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(true, repository.cachedMovies().first { it.id == 2 }.monitored)
        assertEquals(setOf(5), repository.cachedMovies().first { it.id == 2 }.tags)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `edit offline applies optimistically and enqueues a replayable action`() = runTest {
        repository.refresh()
        fake.error = IOException("plane mode")
        val editor = MovieEditorResource(movieIds = listOf(2), monitored = true)
        val outcome = repository.editMovies(editor)
        val queued = (outcome as ArrResult.Ok).value as WriteOutcome.Queued
        assertNotNull(queued.actionId)

        val optimistic = repository.cachedMovies().first { it.id == 2 }
        assertEquals(true, optimistic.monitored)

        val action = queue.pending().single()
        assertEquals("PUT", action.method)
        assertEquals("api/v3/movie/editor", action.path)
        assertEquals(serverId, action.serverId)
        assertTrue(action.bodyJson!!.contains("\"monitored\":true"))
        assertEquals(setOf(2), repository.queuedMovieIds().first())
    }

    @Test
    fun `edit untouched movies keep their fetchedAt`() = runTest {
        repository.refresh()
        fake.error = IOException("offline")
        val editor = MovieEditorResource(movieIds = listOf(2), monitored = true)
        repository.editMovies(editor)
        assertEquals(setOf(2), repository.queuedMovieIds().first())
    }

    @Test
    fun `delete offline removes rows optimistically and enqueues`() = runTest {
        repository.refresh()
        fake.error = IOException("offline")
        val outcome = repository.deleteMovies(listOf(1), deleteFiles = true, addImportExclusion = false)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(listOf("Beta"), repository.cachedMovies().map { it.title })
        val action = queue.pending().single()
        assertEquals("DELETE", action.method)
        assertTrue(action.bodyJson!!.contains("\"deleteFiles\":true"))
    }

    @Test
    fun `delete online removes rows and stays off the queue`() = runTest {
        repository.refresh()
        val outcome = repository.deleteMovies(listOf(1), deleteFiles = false, addImportExclusion = true)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf("Beta"), repository.cachedMovies().map { it.title })
        assertTrue(queue.pending().isEmpty())
        assertEquals(setOf<Int>(), repository.queuedMovieIds().first())
    }

    @Test
    fun `server errors do not enqueue`() = runTest {
        repository.refresh()
        fake.errorOnce = HttpException(Response.error<Any>(409, "conflict".toResponseBody(null)))
        val outcome = repository.editMovies(MovieEditorResource(movieIds = listOf(1), monitored = false))
        val error = (outcome as ArrResult.Err).error
        assertTrue(error is ArrError.Http)
        assertEquals(409, (error as ArrError.Http).code)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `commands dispatch with selection ids`() = runTest {
        val outcome = repository.runCommand(MovieCommand.REFRESH_METADATA, listOf(7, 8))
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf("RefreshMovie" to listOf(7, 8)), commandCalls)
    }

    @Test
    fun `commands offline queue with the movie ids in the body`() = runTest {
        commandResult = ArrResult.err(ArrError.Network(null))
        val outcome = repository.runCommand(MovieCommand.SCAN_DISK, listOf(7))
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v3/command", action.path)
        assertEquals("{\"name\":\"RescanMovie\",\"movieIds\":[7]}", action.bodyJson)
    }

    @Test
    fun `quality profiles cache under their own entity type`() = runTest {
        val result = repository.qualityProfiles()
        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("HD", "UHD"), (result as ArrResult.Ok).value.map { it.name })
        val later = RadarrMoviesRepository(
            serverId = serverId,
            store = store,
            movieApi = fake.movie,
            movieEditorApi = fake.movieEditor,
            qualityProfileApi = { throw IOException("offline") },
            commandRunner = { _, _ -> ArrResult.ok(Unit) },
            actionQueue = queue,
            now = { 1_500L },
            io = io,
        )
        val cached = later.qualityProfiles()
        assertEquals(listOf("HD", "UHD"), (cached as ArrResult.Ok).value.map { it.name })
    }
}
