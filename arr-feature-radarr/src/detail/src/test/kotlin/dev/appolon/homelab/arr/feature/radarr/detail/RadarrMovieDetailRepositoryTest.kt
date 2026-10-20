package dev.appolon.homelab.arr.feature.radarr.detail

import dev.appolon.homelab.arr.api.radarr.model.CreditResource
import dev.appolon.homelab.arr.api.radarr.model.ExtraFileResource
import dev.appolon.homelab.arr.api.radarr.model.HistoryResource
import dev.appolon.homelab.arr.api.radarr.model.MediaCover
import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieFileResource
import dev.appolon.homelab.arr.api.radarr.model.MovieHistoryEventType
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityModel
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Repository behavior: cache-first reads with TTL, stale-fallback on
 * connection loss, and write-through with offline enqueue plus optimistic
 * state.
 */
class RadarrMovieDetailRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var connection: FakeRadarrDetailConnection
    private lateinit var store: FakeDetailEntityStore
    private lateinit var queue: ActionQueueImpl
    private lateinit var repository: RadarrMovieDetailRepository
    private var now: Long = 100_000L

    private val movie = MovieResource(
        id = 1,
        title = "Sunshine",
        year = 2007,
        monitored = false,
        qualityProfileId = 3,
        path = "/movies/Sunshine (2007)",
        sizeOnDisk = 8_000_000_000L,
        images = listOf(
            MediaCover(coverType = MediaCoverTypes.POSTER, url = "/api/v3/mediacover/1/poster.jpg"),
        ),
    )

    @Before
    fun setUp() {
        connection = FakeRadarrDetailConnection()
        store = FakeDetailEntityStore(clock = { now })
        queue = ActionQueueImpl(store)
        repository = RadarrMovieDetailRepository(
            connection = connection,
            store = store,
            queue = queue,
            nowMillis = { now },
        )
        connection.library.movies += movie
    }

    @Test
    fun `movie loads from network then serves from cache inside the ttl`() = runTest {
        val first = repository.movie(serverId, 1)
        assertTrue(first is ArrResult.Ok)

        connection.library.movies[0] = connection.library.movies[0].copy(title = "Changed")
        val cached = repository.movie(serverId, 1)
        assertEquals("Sunshine", (cached as ArrResult.Ok).value.title)

        now += RadarrMovieDetailRepository.DEFAULT_TTL_MILLIS + 1
        val refreshed = repository.movie(serverId, 1)
        assertEquals("Changed", (refreshed as ArrResult.Ok).value.title)
    }

    @Test
    fun `files refresh prunes rows that disappeared server side`() = runTest {
        connection.library.movieFiles += file(id = 10, movieId = 1)
        connection.library.movieFiles += file(id = 11, movieId = 2)
        val first = repository.files(serverId, 1)
        assertEquals(listOf(10), (first as ArrResult.Ok).value.map { it.id })

        now += RadarrMovieDetailRepository.DEFAULT_TTL_MILLIS + 1
        connection.library.movieFiles.removeAll { it.id == 10 }
        connection.library.movieFiles += file(id = 12, movieId = 1)
        val refreshed = repository.files(serverId, 1)
        assertEquals(listOf(12), (refreshed as ArrResult.Ok).value.map { it.id })
        assertNull(
            store.get(serverId, RadarrMovieDetailRepository.MOVIE_FILE, "10", MovieFileResource.serializer()),
        )
    }

    @Test
    fun `stale cache is served when the network fails`() = runTest {
        assertTrue(repository.movie(serverId, 1) is ArrResult.Ok)
        connection.networkUp = false
        now += RadarrMovieDetailRepository.DEFAULT_TTL_MILLIS + 1
        val result = repository.movie(serverId, 1)
        assertEquals("Sunshine", (result as ArrResult.Ok).value.title)
    }

    @Test
    fun `update movie writes through and caches the server value`() = runTest {
        val outcome = repository.updateMovie(
            serverId = serverId,
            current = movie,
            edits = EditableMovieUi(monitored = true, qualityProfileId = 5, path = "/movies/new"),
            moveFiles = true,
        )
        assertFalse(outcome.queued)
        assertNull(outcome.error)
        assertTrue(connection.library.movies.single().monitored)
        assertEquals(5, connection.library.movies.single().qualityProfileId)
        val cached = store.get(serverId, RadarrMovieDetailRepository.MOVIE, "1", MovieResource.serializer())
        assertNotNull(cached)
        assertTrue(cached!!.monitored)
    }

    @Test
    fun `update movie offline queues a replayable put and applies optimistic state`() = runTest {
        connection.networkUp = false
        val outcome = repository.updateMovie(
            serverId = serverId,
            current = movie,
            edits = EditableMovieUi(monitored = true),
            moveFiles = false,
        )
        assertTrue(outcome.queued)
        assertNull(outcome.error)
        assertTrue(outcome.value!!.monitored)

        val pending = queue.pending()
        assertEquals(1, pending.size)
        assertEquals("PUT", pending[0].method)
        assertEquals("api/v3/movie/1", pending[0].path)
        assertTrue(pending[0].bodyJson!!.contains("\"monitored\":true"))

        val cached = store.get(serverId, RadarrMovieDetailRepository.MOVIE, "1", MovieResource.serializer())
        assertTrue(cached!!.monitored)
    }

    @Test
    fun `delete movie online removes cache rows`() = runTest {
        connection.library.movieFiles += file(id = 10, movieId = 1)
        connection.library.extraFiles += ExtraFileResource(id = 20, movieId = 1)
        repository.movie(serverId, 1)
        repository.files(serverId, 1)
        repository.extraFiles(serverId, 1)

        val outcome = repository.deleteMovie(serverId, movie, deleteFiles = true, addImportExclusion = false)
        assertFalse(outcome.queued)
        assertTrue(connection.library.movies.isEmpty())
        assertTrue(connection.library.movieFiles.isEmpty())
        assertNull(
            store.get(serverId, RadarrMovieDetailRepository.MOVIE, "1", MovieResource.serializer()),
        )
        assertEquals(0, store.count(serverId, RadarrMovieDetailRepository.MOVIE_FILE))
        assertEquals(0, store.count(serverId, RadarrMovieDetailRepository.EXTRA_FILE))
    }

    @Test
    fun `delete movie offline queues the delete with its flags`() = runTest {
        connection.networkUp = false
        val outcome = repository.deleteMovie(serverId, movie, deleteFiles = true, addImportExclusion = true)
        assertTrue(outcome.queued)
        val pending = queue.pending().single()
        assertEquals("DELETE", pending.method)
        assertEquals("api/v3/movie/1?deleteFiles=true&addImportExclusion=true", pending.path)
    }

    @Test
    fun `search movie posts the command and offline enqueues it`() = runTest {
        val online = repository.searchMovie(serverId, movie)
        assertFalse(online.queued)
        assertEquals(1, connection.commandBodies.size)
        assertEquals("MoviesSearch", connection.commandBodies.single()["name"]?.toString()?.trim('"'))

        connection.networkUp = false
        val offline = repository.searchMovie(serverId, movie)
        assertTrue(offline.queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v3/command", action.path)
        assertTrue(action.bodyJson!!.contains("MoviesSearch"))
    }

    @Test
    fun `rename and import commands carry their bodies`() = runTest {
        repository.renameFiles(serverId, movie)
        repository.importFolder(serverId, movie.path.orEmpty())
        assertEquals(
            listOf("RenameFiles", "DownloadedMoviesScan"),
            connection.commandBodies.map { it["name"]?.toString()?.trim('"') },
        )
        assertEquals(
            "\"${movie.path}\"",
            connection.commandBodies.last()["path"]?.toString(),
        )
    }

    @Test
    fun `credits history and extra files load and map`() = runTest {
        connection.library.credits += CreditResource(
            id = 30,
            personName = "Actor",
            movieMetadataId = 1,
            type = dev.appolon.homelab.arr.api.radarr.model.CreditType.CAST,
            order = 1,
        )
        connection.queue.historyItems += HistoryResource(
            id = 40,
            movieId = 1,
            eventType = MovieHistoryEventType.GRABBED,
            sourceTitle = "Sunshine.2007.1080p",
        )
        connection.library.extraFiles += ExtraFileResource(
            id = 50,
            movieId = 1,
            relativePath = "Sunshine.en.srt",
            extension = ".srt",
        )
        assertEquals(1, (repository.credits(serverId, 1) as ArrResult.Ok).value.size)
        assertEquals(1, (repository.history(serverId, 1) as ArrResult.Ok).value.size)
        assertEquals(1, (repository.extraFiles(serverId, 1) as ArrResult.Ok).value.size)
    }

    @Test
    fun `edit options load profiles and tags`() = runTest {
        connection.calendar.qualityProfiles += QualityProfileResource(id = 3, name = "HD")
        connection.system.tagItems += TagResource(id = 8, label = "watchlist")
        val options = repository.editOptions(serverId)
        assertEquals(listOf("HD"), options.qualityProfiles.map { it.name })
        assertEquals(listOf("watchlist"), options.tags.map { it.name })
        assertEquals("HD", options.qualityProfileName(3))
        assertNull(options.qualityProfileName(4))
    }

    private fun file(id: Int, movieId: Int) = MovieFileResource(
        id = id,
        movieId = movieId,
        relativePath = "movie-$id.mkv",
        size = 1_000L * id,
        quality = QualityModel(quality = Quality(id = 1, name = "Bluray-1080p")),
    )
}
