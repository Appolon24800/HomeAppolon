package dev.appolon.homelab.arr.feature.radarr.addnew.data

import dev.appolon.homelab.arr.api.radarr.FakeRadarrDiscovery
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.fake.FakeRadarrLibrary
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.addnew.FakeActionQueue
import dev.appolon.homelab.arr.feature.radarr.addnew.FakeEntityStore
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

class RadarrAddNewRepositoryTest {

    private val serverId = ServerId("s1")
    private val io = UnconfinedTestDispatcher()
    private val MOVIE_TYPE = RadarrAddNewRepository.MOVIE_TYPE
    private lateinit var library: FakeRadarrLibrary
    private lateinit var discovery: FakeRadarrDiscovery
    private lateinit var system: FakeRadarrSystem
    private lateinit var calendar: FakeRadarrCalendar
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var repository: RadarrAddNewRepository
    private var now: Long = 1_000L

    @Before
    fun setUp() {
        library = FakeRadarrLibrary()
        discovery = FakeRadarrDiscovery()
        system = FakeRadarrSystem()
        calendar = FakeRadarrCalendar()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        repository = newRepository()
        library.movies += listOf(
            MovieResource(id = 1, title = "Alpha", tmdbId = 101, year = 2001, hasFile = true),
        )
        library.lookupCatalog += listOf(
            MovieResource(title = "Dune", tmdbId = 11, year = 2021),
            MovieResource(title = "Dune", tmdbId = 12, year = 1984),
        )
        system.rootFolderItems += dev.appolon.homelab.arr.api.radarr.model.RootFolderResource(
            id = 1,
            path = "/media/films",
            freeSpace = 5_000_000_000L,
        )
        calendar.qualityProfiles += dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource(
            id = 1,
            name = "HD",
        )
        system.tagItems += dev.appolon.homelab.arr.api.radarr.model.TagResource(
            id = 4,
            label = "kids",
        )
        discovery.discoveredMovies += ImportListMoviesResource(
            tmdbId = 11,
            title = "Dune",
            year = 2021,
            isRecommendation = true,
        )
    }

    private fun newRepository(): RadarrAddNewRepository = RadarrAddNewRepository(
        serverId = serverId,
        store = store,
        movieLookupApi = library.movieLookup,
        movieApi = library.movie,
        movieImportApi = library.movieImport,
        importListMoviesApi = discovery,
        rootFolderApi = system,
        tagApi = system,
        qualityProfileApi = calendar,
        actionQueue = queue,
        now = { now },
        io = io,
    )

    @Test
    fun `search strips the disambiguation year and ranks it first`() = runTest {
        val result = repository.search("Dune (1984)")
        assertTrue(result is ArrResult.Ok)
        val titles = (result as ArrResult.Ok).value
        assertEquals(listOf(1984, 2021), titles.map { it.year })
    }

    @Test
    fun `blank search returns empty without touching the server`() = runTest {
        library.error = IOException("must not be called")
        val result = repository.search("   ")
        assertTrue(result is ArrResult.Ok)
        assertTrue((result as ArrResult.Ok).value.isEmpty())
    }

    @Test
    fun `search failure surfaces the error`() = runTest {
        library.error = HttpException(Response.error<Unit>(500, "{}".toResponseBody()))
        val result = repository.search("Dune")
        assertTrue((result as ArrResult.Err).error is ArrError.Http)
    }

    @Test
    fun `syncLibraryIfStale reconciles rows and preserves optimistic adds`() = runTest {
        repository.syncLibraryIfStale(ttlMillis = 0)
        assertEquals(1, store.getAll(serverId, MOVIE_TYPE, MovieResource.serializer()).size)

        // Simulate an offline add that has not replayed yet.
        store.put(
            serverId,
            MOVIE_TYPE,
            RadarrAddNewRepository.optimisticKey(999),
            MovieResource(id = 0, title = "Queued", tmdbId = 999),
            MovieResource.serializer(),
        )

        library.movies.clear()
        repository.syncLibraryIfStale(ttlMillis = 0)
        val cached = store.getAll(serverId, MOVIE_TYPE, MovieResource.serializer())
        assertEquals(listOf("Queued"), cached.map { it.title })
        assertTrue(
            store.rowKeys().contains(
                Triple(serverId, MOVIE_TYPE, RadarrAddNewRepository.optimisticKey(999)),
            ),
        )
    }

    @Test
    fun `root folders serve cache within the ttl and refetch after`() = runTest {
        val first = repository.rootFolders()
        assertEquals(listOf("/media/films"), (first as ArrResult.Ok).value.map { it.path })

        system.rootFolderItems.clear()
        val cached = repository.rootFolders()
        assertEquals(1, (cached as ArrResult.Ok).value.size)

        now += RadarrAddNewRepository.CATALOG_TTL_MILLIS + 1
        val refreshed = repository.rootFolders()
        assertTrue((refreshed as ArrResult.Ok).value.isEmpty())
    }

    @Test
    fun `quality profiles and tags load cache-first`() = runTest {
        val profiles = repository.qualityProfiles()
        assertEquals(listOf("HD"), (profiles as ArrResult.Ok).value.map { it.name })
        val tags = repository.tags()
        assertEquals(listOf("kids"), (tags as ArrResult.Ok).value.map { it.label })
    }

    @Test
    fun `addMovie syncs and writes the created movie through`() = runTest {
        val payload = AddNewLogic.toAddMoviePayload(
            candidate = MovieResource(title = "Dune", tmdbId = 12, year = 1984),
            rootFolderPath = "/media/films",
            qualityProfileId = 1,
            monitor = MonitorTypes.MOVIE_ONLY,
            minimumAvailability = MovieStatusType.RELEASED,
            tags = setOf(4),
            searchForMovie = true,
        )
        val outcome = repository.addMovie(payload)
        assertTrue((outcome as ArrResult.Ok).value is AddOutcome.Synced)
        val added = library.movies.first { it.tmdbId == 12 }
        assertTrue(added.id != 0)
        val cachedAdded = store
            .getAll(serverId, MOVIE_TYPE, MovieResource.serializer())
            .single { it.tmdbId == 12 }
        assertEquals(added.id, cachedAdded.id)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `addMovie offline queues a replayable action and applies optimistically`() = runTest {
        library.error = IOException("plane mode")
        val payload = AddNewLogic.toAddMoviePayload(
            candidate = MovieResource(title = "Dune", tmdbId = 12, year = 1984),
            rootFolderPath = "/media/films",
            qualityProfileId = 1,
            monitor = MonitorTypes.MOVIE_ONLY,
            minimumAvailability = MovieStatusType.RELEASED,
            tags = emptySet(),
            searchForMovie = false,
        )
        val outcome = repository.addMovie(payload)
        val actionId = (outcome as ArrResult.Ok).value.let { it as AddOutcome.Queued }.actionId
        val action = queue.pending().single()
        assertEquals(actionId, action.actionId)
        assertEquals("POST", action.method)
        assertEquals(RadarrAddNewRepository.ADD_PATH, action.path)
        assertTrue(action.bodyJson.orEmpty().contains("\"tmdbId\":12"))
        assertTrue(
            store.rowKeys().contains(
                Triple(serverId, MOVIE_TYPE, RadarrAddNewRepository.optimisticKey(12)),
            ),
        )
        assertEquals(setOf(12), repository.queuedTmdbIds().first())
    }

    @Test
    fun `addMovie non-retryable failure does not queue`() = runTest {
        library.error = HttpException(Response.error<Unit>(400, "{}".toResponseBody()))
        val payload = MovieResource(title = "Dune", tmdbId = 12)
        val outcome = repository.addMovie(payload)
        assertTrue((outcome as ArrResult.Err).error is ArrError.Http)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `importMovies writes returned movies through`() = runTest {
        val payloads = listOf(
            AddNewLogic.toAddMoviePayload(
                candidate = MovieResource(title = "Dune", tmdbId = 12, year = 1984),
                rootFolderPath = "/media/films",
                qualityProfileId = 1,
                monitor = MonitorTypes.MOVIE_ONLY,
                minimumAvailability = MovieStatusType.RELEASED,
                tags = emptySet(),
                searchForMovie = false,
            ),
        )
        val outcome = repository.importMovies(payloads)
        assertTrue((outcome as ArrResult.Ok).value is AddOutcome.Synced)
        assertEquals(2, library.movies.size)
        assertTrue(
            store.getAll(serverId, MOVIE_TYPE, MovieResource.serializer()).any { it.tmdbId == 12 },
        )
    }

    @Test
    fun `importMovies offline queues one batch action with every tmdbId`() = runTest {
        library.error = IOException("offline")
        val payloads = listOf(
            MovieResource(title = "Dune", tmdbId = 12, year = 1984),
            MovieResource(title = "Heat", tmdbId = 949, year = 1995),
        )
        val outcome = repository.importMovies(payloads)
        assertTrue((outcome as ArrResult.Ok).value is AddOutcome.Queued)
        val action = queue.pending().single()
        assertEquals(RadarrAddNewRepository.IMPORT_PATH, action.path)
        assertEquals(setOf(12, 949), repository.queuedTmdbIds().first())
    }

    @Test
    fun `discover caches the feed and drops stale rows on refresh`() = runTest {
        val first = repository.discover()
        assertEquals(listOf(11), (first as ArrResult.Ok).value.map { it.tmdbId })

        discovery.discoveredMovies.clear()
        discovery.discoveredMovies += ImportListMoviesResource(
            tmdbId = 77,
            title = "New",
            isPopular = true,
        )
        now += RadarrAddNewRepository.DISCOVERY_TTL_MILLIS + 1
        val refreshed = repository.discover()
        assertEquals(listOf(77), (refreshed as ArrResult.Ok).value.map { it.tmdbId })
        assertEquals(
            listOf("radarr.discovery"),
            store.rowKeys()
                .filter { it.first == serverId && it.third == "77" }
                .map { it.second.value },
        )
    }

    @Test
    fun `queuedCount counts only this server's actions`() = runTest {
        assertEquals(0, repository.queuedCount().first())
    }
}
