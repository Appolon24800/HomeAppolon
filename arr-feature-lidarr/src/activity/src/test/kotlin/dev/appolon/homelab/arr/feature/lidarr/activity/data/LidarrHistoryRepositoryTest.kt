package dev.appolon.homelab.arr.feature.lidarr.activity.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.lidarr.activity.FakeActivityEntityStore
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class LidarrHistoryRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeLidarrQueue
    private lateinit var store: FakeActivityEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private var fakeNow: () -> Long = { 1_000L }

    private fun repo(
        context: kotlin.coroutines.CoroutineContext = StandardTestDispatcher(),
    ): LidarrHistoryRepository = LidarrHistoryRepository(
        serverId = serverId,
        store = store,
        historyApi = fake,
        actionQueue = actionQueue,
        now = fakeNow,
        ioContext = context,
    )

    private fun event(id: Int, date: String) = HistoryResource(
        id = id,
        albumId = id,
        artistId = id,
        trackId = id,
        sourceTitle = "Release.$id",
        date = date,
        eventType = EntityHistoryEventType.GRABBED,
    )

    @Before
    fun setUp() {
        fake = FakeLidarrQueue()
        store = FakeActivityEntityStore()
        actionQueue = ActionQueueImpl(store)
        fakeNow = { 1_000L }
    }

    @Test
    fun `refresh recenters the cache on page one`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        repeat(3) { index -> fake.history[index + 1] = event(index + 1, "2026-10-01T00:00:00Z") }
        store.put(
            serverId,
            LidarrHistoryRepository.HISTORY_EVENT_TYPE,
            "99",
            event(99, "2026-01-01T00:00:00Z"),
            HistoryResource.serializer(),
        )

        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        val meta = (result as ArrResult.Ok).value
        assertEquals(1, meta.pagesLoaded)
        assertEquals(3, meta.totalRecords)
        assertEquals(listOf(1, 2, 3), repository.cachedEvents().map { it.id }.sorted())
        assertTrue(store.lastFetchedAt(serverId, LidarrHistoryRepository.HISTORY_EVENT_TYPE)!! > 0)
    }

    @Test
    fun `isStale flips once the TTL elapsed`() = runTest {
        var now = 1_000L
        fakeNow = { now }
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1, "2026-10-01T00:00:00Z")
        repository.refresh()
        assertFalse(repository.isStale(ttlMillis = 100))

        now += 101
        assertTrue(repository.isStale(ttlMillis = 100))
    }

    @Test
    fun `loadNextPage appends the next page without extending the TTL`() = runTest {
        var now = 1_000L
        fakeNow = { now }
        val repository = repo(StandardTestDispatcher(testScheduler))
        // Strictly descending dates keep the fake's date sort aligned with id order.
        repeat(LIDARR_ACTIVITY_PAGE_SIZE + 5) { index ->
            fake.history[index + 1] = event(
                index + 1,
                java.time.Instant.ofEpochSecond(1_800_000_000L - index * 3_600L).toString(),
            )
        }
        repository.refresh()
        val stamp = store.lastFetchedAt(serverId, LidarrHistoryRepository.HISTORY_EVENT_TYPE)
        now += 500

        val load = repository.loadNextPage()

        assertTrue(load is ArrResult.Ok)
        val pageLoad = (load as ArrResult.Ok).value
        assertEquals(5, pageLoad.appended)
        assertEquals(2, pageLoad.meta.pagesLoaded)
        assertTrue(pageLoad.endReached)
        assertEquals(LIDARR_ACTIVITY_PAGE_SIZE + 5, repository.cachedEvents().size)
        assertEquals(
            stamp,
            store.lastFetchedAt(serverId, LidarrHistoryRepository.HISTORY_EVENT_TYPE),
        )
    }

    @Test
    fun `loadNextPage reports the end once every record is cached`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1, "2026-10-01T00:00:00Z")
        repository.refresh()

        val load = repository.loadNextPage()

        assertTrue(load is ArrResult.Ok)
        assertTrue((load as ArrResult.Ok).value.endReached)
        assertEquals(0, load.value.appended)
    }

    @Test
    fun `refreshIfStale skips the network while fresh`() = runTest {
        var now = 1_000L
        fakeNow = { now }
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1, "2026-10-01T00:00:00Z")
        repository.refresh()
        val stamp = store.lastFetchedAt(serverId, LidarrHistoryRepository.HISTORY_EVENT_TYPE)

        val result = repository.refreshIfStale(ttlMillis = 60_000)

        assertTrue(result is ArrResult.Ok)
        assertEquals(stamp, store.lastFetchedAt(serverId, LidarrHistoryRepository.HISTORY_EVENT_TYPE))
    }

    @Test
    fun `markFailed posts to the server and flips the cached row`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.history[7] = event(7, "2026-10-01T00:00:00Z")
        repository.refresh()

        val result = repository.markFailed(7)

        assertTrue(result is ArrResult.Ok)
        assertEquals(WriteOutcome.Synced, (result as ArrResult.Ok).value)
        assertEquals(EntityHistoryEventType.DOWNLOAD_FAILED, repository.cachedEvents().single().eventType)
        assertTrue(7 in fake.failedHistoryIds)
    }

    @Test
    fun `markFailed offline enqueues the post and applies optimistically`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.history[7] = event(7, "2026-10-01T00:00:00Z")
        repository.refresh()
        fake.error = { if (it == "failHistory") IOException("offline") else null }

        val result = repository.markFailed(7)

        assertTrue(result is ArrResult.Ok)
        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        val queued = actionQueue.pending().single()
        assertEquals("POST", queued.method)
        assertEquals("api/v1/history/failed/7", queued.path)
        assertEquals(EntityHistoryEventType.DOWNLOAD_FAILED, repository.cachedEvents().single().eventType)
        assertEquals(setOf(7), repository.queuedMarkFailedIds().first())
    }

    @Test
    fun `markFailed surfaces auth failures instead of enqueueing`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.history[7] = event(7, "2026-10-01T00:00:00Z")
        repository.refresh()
        fake.error = {
            if (it == "failHistory") {
                HttpException(Response.error<Unit>(403, "{}".toResponseBody("application/json".toMediaType())))
            } else {
                null
            }
        }

        val result = repository.markFailed(7)

        assertTrue(result is ArrResult.Err)
        assertTrue((result as ArrResult.Err).error is ArrError.Auth)
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `refresh maps network failures to ArrError`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.error = { if (it == "getHistory") IOException("offline") else null }

        val result = repository.refresh()

        assertTrue(result is ArrResult.Err)
        assertTrue(actionQueue.pending().isEmpty())
    }
}
