package dev.appolon.homelab.arr.feature.lidarr.wanted.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrDiscovery
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.wanted.FakeWantedActionQueue
import dev.appolon.homelab.arr.feature.lidarr.wanted.FakeWantedEntityStore
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LidarrWantedRepositoryTest {

    private val serverId = ServerId("s1")
    private val io = UnconfinedTestDispatcher()
    private lateinit var fake: FakeLidarrDiscovery
    private lateinit var store: FakeWantedEntityStore
    private lateinit var queue: FakeWantedActionQueue
    private lateinit var repository: LidarrWantedRepository

    private val commandCalls = mutableListOf<Pair<String, List<Int>?>>()
    private var commandResult: ArrResult<Unit> = ArrResult.ok(Unit)

    @Before
    fun setUp() {
        fake = FakeLidarrDiscovery()
        store = FakeWantedEntityStore()
        queue = FakeWantedActionQueue()
        commandCalls.clear()
        repository = newRepository()
        repeat(12) { artist ->
            fake.artists[artist + 1] = artist(artist + 1)
        }
        repeat(120) { index ->
            fake.missing[index + 1] = album(index + 1)
        }
    }

    private fun artist(id: Int) = ArtistResource(
        id = id,
        artistName = "Artist %02d".format(id),
        sortName = "artist %02d".format(id),
    )

    private fun album(
        id: Int,
        artistId: Int = (id - 1) / 10 + 1,
        albumType: String = "Album",
    ) = AlbumResource(
        id = id,
        title = "Album %03d".format(id),
        artistId = artistId,
        albumType = albumType,
        releaseDate = "2026-01-%02d".format((id % 28) + 1),
        monitored = true,
    )

    private fun newRepository() = LidarrWantedRepository(
        serverId = serverId,
        store = store,
        missingApi = fake,
        cutoffApi = fake,
        commandRunner = { name, albumIds ->
            commandCalls += name to albumIds
            commandResult
        },
        actionQueue = queue,
        now = { 1_000L },
        ioContext = io,
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
    fun `refresh joins the artist into every row`() = runTest {
        repository.refresh(WantedTab.MISSING)
        val cached = repository.cached(WantedTab.MISSING)
        assertTrue(cached.all { it.artist?.artistName != null })
        assertEquals("Artist 01", cached.first { it.id == 1 }.artist?.artistName)
    }

    @Test
    fun `refresh drops rows the server no longer lists`() = runTest {
        repository.refresh(WantedTab.MISSING)
        fake.missing.clear()
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
        fake.cutoff[501] = album(501, artistId = 11)
        repository.refresh(WantedTab.MISSING)
        repository.refresh(WantedTab.CUTOFF)
        assertTrue(
            store.get(
                serverId,
                WantedTab.MISSING.entityType,
                "1",
                AlbumResource.serializer(),
            ) != null,
        )
        assertTrue(
            store.get(
                serverId,
                WantedTab.CUTOFF.entityType,
                "501",
                AlbumResource.serializer(),
            ) != null,
        )
    }

    @Test
    fun `isStale honors the ttl`() = runTest {
        assertTrue(repository.isStale(WantedTab.MISSING))
        repository.refresh(WantedTab.MISSING)
        assertTrue(!repository.isStale(WantedTab.MISSING))
        val aged = LidarrWantedRepository(
            serverId = serverId,
            store = store,
            missingApi = fake,
            cutoffApi = fake,
            commandRunner = { _, _ -> ArrResult.ok(Unit) },
            actionQueue = queue,
            now = { 1_000L + LidarrWantedRepository.WANTED_TTL_MILLIS + 1 },
            ioContext = io,
        )
        assertTrue(aged.isStale(WantedTab.MISSING))
    }

    @Test
    fun `http failure surfaces as error and keeps the cache`() = runTest {
        repository.refresh(WantedTab.MISSING)
        fake.error = { operation ->
            if (operation == "getMissing") {
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
    fun `album search dispatches AlbumSearch with the ids`() = runTest {
        val outcome = repository.searchAlbums(listOf(7, 9))
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf("AlbumSearch" to listOf(7, 9)), commandCalls)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `album search queues for replay when offline`() = runTest {
        commandResult = ArrResult.err(ArrError.Offline())
        val outcome = repository.searchAlbums(listOf(7))
        val queued = (outcome as ArrResult.Ok).value
        assertTrue(queued is WriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v1/command", action.path)
        assertTrue(action.bodyJson!!.contains("AlbumSearch"))
        assertTrue(action.bodyJson!!.contains("7"))
        assertEquals(setOf(7), repository.queuedSearchAlbumIds().first())
    }

    @Test
    fun `album search fails outright on auth errors`() = runTest {
        commandResult = ArrResult.err(ArrError.Auth(401))
        val outcome = repository.searchAlbums(listOf(7))
        assertTrue(outcome is ArrResult.Err)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `network throw queues like offline`() = runTest {
        commandResult = ArrResult.err(ArrError.Network(IOException()))
        val outcome = repository.searchAlbums(listOf(7, 8))
        assertTrue(((outcome as ArrResult.Ok).value) is WriteOutcome.Queued)
        assertEquals(setOf(7, 8), repository.queuedSearchAlbumIds().first())
    }

    @Test
    fun `empty search dispatch is a no-op`() = runTest {
        val outcome = repository.searchAlbums(emptyList())
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(commandCalls.isEmpty())
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `counts merge both tabs as they refresh`() = runTest {
        fake.missing.clear()
        fake.missing[1] = album(1)
        fake.cutoff.clear()
        repeat(5) { index -> fake.cutoff[index + 1] = album(index + 1) }
        assertEquals(WantedCounts(), repository.observeCounts().first())
        repository.refresh(WantedTab.MISSING)
        assertEquals(WantedCounts(missing = 1), repository.observeCounts().first())
        repository.refresh(WantedTab.CUTOFF)
        assertEquals(WantedCounts(missing = 1, cutoff = 5), repository.observeCounts().first())
    }
}
