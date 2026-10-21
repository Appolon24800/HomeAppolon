package dev.appolon.homelab.arr.feature.sonarr.activity.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeHistoryEventType
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SonarrHistoryRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeSonarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private var fakeNow: () -> Long = { 1_000L }

    private fun repo(
        context: kotlin.coroutines.CoroutineContext = StandardTestDispatcher(),
    ): SonarrHistoryRepository = SonarrHistoryRepository(
        serverId = serverId,
        store = store,
        historyApi = fake.history,
        actionQueue = actionQueue,
        now = fakeNow,
        ioContext = context,
    )

    private fun event(id: Int, date: String) = HistoryResource(
        id = id,
        episodeId = id,
        seriesId = id,
        sourceTitle = "Release.$id",
        date = date,
        eventType = EpisodeHistoryEventType.GRABBED,
    )

    @Before
    fun setUp() {
        fake = FakeSonarrQueue()
        store = FakeEntityStore()
        actionQueue = ActionQueueImpl(store)
        fakeNow = { 1_000L }
    }

    @Test
    fun `refresh recenters the cache on page one`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        repeat(3) { index -> fake.historyStore[index + 1] = event(index + 1, "2026-10-01T00:00:00Z") }
        store.put(
            serverId,
            SonarrHistoryRepository.HISTORY_EVENT_TYPE,
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
        assertTrue(store.lastFetchedAt(serverId, SonarrHistoryRepository.HISTORY_EVENT_TYPE)!! > 0)
    }

    @Test
    fun `isStale flips once the TTL elapsed`() = runTest {
        var now = 1_000L
        fakeNow = { now }
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.historyStore[1] = event(1, "2026-10-01T00:00:00Z")
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
        repeat(ACTIVITY_PAGE_SIZE + 5) { index ->
            fake.historyStore[index + 1] = event(
                index + 1,
                java.time.Instant.ofEpochSecond(1_800_000_000L - index * 3_600L).toString(),
            )
        }
        repository.refresh()
        val stamp = store.lastFetchedAt(serverId, SonarrHistoryRepository.HISTORY_EVENT_TYPE)
        now += 500

        val load = repository.loadNextPage()

        assertTrue(load is ArrResult.Ok)
        val pageLoad = (load as ArrResult.Ok).value
        assertEquals(5, pageLoad.appended)
        assertEquals(2, pageLoad.meta.pagesLoaded)
        assertTrue(pageLoad.endReached)
        assertEquals(ACTIVITY_PAGE_SIZE + 5, repository.cachedEvents().size)
        assertEquals(
            stamp,
            store.lastFetchedAt(serverId, SonarrHistoryRepository.HISTORY_EVENT_TYPE),
        )
    }

    @Test
    fun `loadNextPage reports the end once every record is cached`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.historyStore[1] = event(1, "2026-10-01T00:00:00Z")
        repository.refresh()

        val load = repository.loadNextPage()

        assertTrue(load is ArrResult.Ok)
        val pageLoad = (load as ArrResult.Ok).value
        assertEquals(0, pageLoad.appended)
        assertTrue(pageLoad.endReached)
    }

    @Test
    fun `loadNextPage stops at the page cap even when the server reports more`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        repository.putMetaForTest(pagesLoaded = ACTIVITY_MAX_PAGES, total = Int.MAX_VALUE)

        val load = repository.loadNextPage()

        assertTrue(load is ArrResult.Ok)
        val pageLoad = (load as ArrResult.Ok).value
        assertTrue(pageLoad.endReached)
        assertEquals(0, pageLoad.appended)
    }

    @Test
    fun `markFailed online flips the cached event type`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.historyStore[1] = event(1, "2026-10-01T00:00:00Z")
        repository.refresh()

        val result = repository.markFailed(1)

        assertTrue(result is ArrResult.Ok && result.value == WriteOutcome.Synced)
        assertEquals(
            EpisodeHistoryEventType.DOWNLOAD_FAILED,
            repository.cachedEvents().single().eventType,
        )
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `markFailed offline flips optimistically and enqueues the POST`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.historyStore[1] = event(1, "2026-10-01T00:00:00Z")
        repository.refresh()
        fake.error = { op -> if (op == "History.markFailed") IOException("offline") else null }

        val result = repository.markFailed(1)

        assertTrue(result is ArrResult.Ok && result.value is WriteOutcome.Queued)
        assertEquals(
            EpisodeHistoryEventType.DOWNLOAD_FAILED,
            repository.cachedEvents().single().eventType,
        )
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v3/history/failed/1", pending.path)
    }

    @Test
    fun `non-retryable markFailed failures surface instead of queueing`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.historyStore[1] = event(1, "2026-10-01T00:00:00Z")
        repository.refresh()
        fake.error = { op ->
            if (op == "History.markFailed") HttpException(Response.error<Any>(401, "".toResponseBody())) else {
                null
            }
        }

        val result = repository.markFailed(1)

        assertTrue(result is ArrResult.Err && result.error is ArrError.Auth)
        assertTrue(actionQueue.pending().isEmpty())
        assertEquals(
            EpisodeHistoryEventType.GRABBED,
            repository.cachedEvents().single().eventType,
        )
    }

    @Test
    fun `queuedMarkFailedIds projects the pending POST for this server only`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.historyStore[1] = event(1, "2026-10-01T00:00:00Z")
        repository.refresh()
        fake.error = { op -> if (op == "History.markFailed") IOException("offline") else null }
        repository.markFailed(1)

        assertEquals(setOf(1), repository.queuedMarkFailedIds().first())
        assertEquals(1, repository.queuedCount().first())
    }

    private suspend fun SonarrHistoryRepository.putMetaForTest(pagesLoaded: Int, total: Int) {
        store.put(
            serverId,
            SonarrHistoryRepository.META_TYPE,
            SonarrHistoryRepository.META_ID,
            ActivityListMeta(totalRecords = total, pagesLoaded = pagesLoaded),
            ActivityListMeta.serializer(),
        )
    }
}
