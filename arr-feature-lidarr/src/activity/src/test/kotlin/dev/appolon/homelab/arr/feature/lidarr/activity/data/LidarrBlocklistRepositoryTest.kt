package dev.appolon.homelab.arr.feature.lidarr.activity.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.BlocklistResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class LidarrBlocklistRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeLidarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private var fakeNow: () -> Long = { 1_000L }

    private fun repo(
        context: kotlin.coroutines.CoroutineContext = StandardTestDispatcher(),
    ): LidarrBlocklistRepository = LidarrBlocklistRepository(
        serverId = serverId,
        store = store,
        blocklistApi = fake,
        actionQueue = actionQueue,
        now = fakeNow,
        ioContext = context,
    )

    private fun entry(id: Int, date: String = "2026-10-01T00:00:00Z") = BlocklistResource(
        id = id,
        artistId = id,
        sourceTitle = "Release.$id",
        date = date,
        message = "Release rejected",
    )

    @Before
    fun setUp() {
        fake = FakeLidarrQueue()
        store = FakeEntityStore()
        actionQueue = ActionQueueImpl(store)
        fakeNow = { 1_000L }
    }

    @Test
    fun `refresh caches page one and records the cursor`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        fake.blocklist[2] = entry(2, "2026-09-01T00:00:00Z")

        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        val meta = (result as ArrResult.Ok).value
        assertEquals(1, meta.pagesLoaded)
        assertEquals(2, meta.totalRecords)
        assertEquals(setOf(1, 2), repository.cachedEntries().map { it.id }.toSet())
        assertTrue(repository.queuedRemovedIds().first().isEmpty())
    }

    @Test
    fun `removeEntry deletes on the server and from the cache`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        repository.refresh()

        val result = repository.removeEntry(1)

        assertTrue(result is ArrResult.Ok)
        assertEquals(WriteOutcome.Synced, (result as ArrResult.Ok).value)
        assertTrue(repository.cachedEntries().isEmpty())
        assertTrue(fake.blocklist.isEmpty())
    }

    @Test
    fun `removeEntry offline enqueues the delete and removes optimistically`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        repository.refresh()
        fake.error = { if (it == "deleteBlocklist") IOException("offline") else null }

        val result = repository.removeEntry(1)

        assertTrue(result is ArrResult.Ok)
        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        val queued = actionQueue.pending().single()
        assertEquals("DELETE", queued.method)
        assertEquals("api/v1/blocklist/1", queued.path)
        assertTrue(repository.cachedEntries().isEmpty())
        assertEquals(setOf(1), repository.queuedRemovedIds().first())
    }

    @Test
    fun `clearAll bulk deletes every loaded entry`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        fake.blocklist[2] = entry(2)
        repository.refresh()

        val result = repository.clearAll()

        assertTrue(result is ArrResult.Ok)
        assertEquals(WriteOutcome.Synced, (result as ArrResult.Ok).value)
        assertTrue(repository.cachedEntries().isEmpty())
        assertTrue(fake.blocklist.isEmpty())
    }

    @Test
    fun `clearAll offline enqueues one bulk delete covering every id`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        fake.blocklist[2] = entry(2)
        repository.refresh()
        fake.error = { if (it == "deleteBlocklistBulk") IOException("offline") else null }

        val result = repository.clearAll()

        assertTrue(result is ArrResult.Ok)
        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        val queued = actionQueue.pending().single()
        assertEquals("DELETE", queued.method)
        assertEquals("api/v1/blocklist/bulk", queued.path)
        val queuedIds = ArrJson.decodeFromString(
            BlocklistBulkResource.serializer(),
            queued.bodyJson!!,
        ).ids.orEmpty()
        assertEquals(setOf(1, 2), queuedIds.toSet())
        assertTrue(repository.cachedEntries().isEmpty())
        assertEquals(setOf(1, 2), repository.queuedRemovedIds().first())
    }

    @Test
    fun `clearAll with an empty cache reports success without a call`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))

        val result = repository.clearAll()

        assertTrue(result is ArrResult.Ok)
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `removal failures that are not retryable surface as errors`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        repository.refresh()
        fake.error = {
            if (it == "deleteBlocklist") {
                HttpException(
                    Response.error<Unit>(404, "{}".toResponseBody("application/json".toMediaType())),
                )
            } else {
                null
            }
        }

        val result = repository.removeEntry(1)

        assertTrue(result is ArrResult.Err)
        assertTrue(actionQueue.pending().isEmpty())
        assertFalse(repository.cachedEntries().isEmpty())
    }

    @Test
    fun `isStale flips once the TTL elapsed`() = runTest {
        var now = 1_000L
        fakeNow = { now }
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        repository.refresh()
        assertFalse(repository.isStale(ttlMillis = 100))

        now += 101
        assertTrue(repository.isStale(ttlMillis = 100))
    }

    @Test
    fun `loadNextPage appends pages until the server total`() = runTest {
        var now = 1_000L
        fakeNow = { now }
        val repository = repo(StandardTestDispatcher(testScheduler))
        repeat(LIDARR_ACTIVITY_PAGE_SIZE + 5) { index ->
            fake.blocklist[index + 1] = entry(
                index + 1,
                java.time.Instant.ofEpochSecond(1_800_000_000L - index * 3_600L).toString(),
            )
        }
        repository.refresh()

        val load = repository.loadNextPage()

        assertTrue(load is ArrResult.Ok)
        val pageLoad = (load as ArrResult.Ok).value
        assertEquals(5, pageLoad.appended)
        assertTrue(pageLoad.endReached)
        assertEquals(LIDARR_ACTIVITY_PAGE_SIZE + 5, repository.cachedEntries().size)
    }
}
