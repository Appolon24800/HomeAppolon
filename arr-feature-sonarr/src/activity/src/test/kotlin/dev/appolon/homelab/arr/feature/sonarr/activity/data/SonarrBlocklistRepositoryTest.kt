package dev.appolon.homelab.arr.feature.sonarr.activity.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.BlocklistResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.sonarr.activity.FakeActivityEntityStore
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
class SonarrBlocklistRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeSonarrQueue
    private lateinit var store: FakeActivityEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private var fakeNow: () -> Long = { 1_000L }

    private fun repo(
        context: kotlin.coroutines.CoroutineContext = StandardTestDispatcher(),
    ): SonarrBlocklistRepository = SonarrBlocklistRepository(
        serverId = serverId,
        store = store,
        blocklistApi = fake.blocklist,
        actionQueue = actionQueue,
        now = fakeNow,
        ioContext = context,
    )

    private fun entry(id: Int, date: String = "2026-10-01T00:00:00Z") = BlocklistResource(
        id = id,
        seriesId = id,
        sourceTitle = "Release.$id",
        date = date,
        message = "Rejected",
    )

    @Before
    fun setUp() {
        fake = FakeSonarrQueue()
        store = FakeActivityEntityStore()
        actionQueue = ActionQueueImpl(store)
        fakeNow = { 1_000L }
    }

    @Test
    fun `refresh recenters the cache and records the paging meta`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        fake.blocklistStore[2] = entry(2)

        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        val meta = (result as ArrResult.Ok).value
        assertEquals(2, meta.totalRecords)
        assertEquals(1, meta.pagesLoaded)
        assertEquals(listOf(1, 2), repository.cachedEntries().map { it.id }.sorted())
    }

    @Test
    fun `isStale flips once the TTL elapsed`() = runTest {
        var now = 1_000L
        fakeNow = { now }
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        repository.refresh()
        assertFalse(repository.isStale(ttlMillis = 100))

        now += 101
        assertTrue(repository.isStale(ttlMillis = 100))
    }

    @Test
    fun `remove online drops the cached row`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        fake.blocklistStore[2] = entry(2)
        repository.refresh()

        val result = repository.removeEntry(1)

        assertTrue(result is ArrResult.Ok && result.value == WriteOutcome.Synced)
        assertEquals(listOf(2), repository.cachedEntries().map { it.id })
        assertEquals(listOf(2), fake.blocklistStore.keys.toList())
    }

    @Test
    fun `remove offline optimistically clears the row and enqueues the DELETE`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        repository.refresh()
        fake.error = { op -> if (op == "Blocklist.delete") IOException("offline") else null }

        val result = repository.removeEntry(1)

        assertTrue(result is ArrResult.Ok && result.value is WriteOutcome.Queued)
        assertTrue(repository.cachedEntries().isEmpty())
        val pending = actionQueue.pending().single()
        assertEquals("DELETE", pending.method)
        assertEquals("api/v3/blocklist/1", pending.path)
    }

    @Test
    fun `clearAll online bulk deletes every cached entry`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        fake.blocklistStore[2] = entry(2)
        repository.refresh()

        val result = repository.clearAll()

        assertTrue(result is ArrResult.Ok && result.value == WriteOutcome.Synced)
        assertTrue(repository.cachedEntries().isEmpty())
        assertTrue(fake.blocklistStore.isEmpty())
    }

    @Test
    fun `clearAll offline clears optimistically and enqueues the bulk DELETE`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        fake.blocklistStore[2] = entry(2)
        repository.refresh()
        fake.error = { op -> if (op == "Blocklist.deleteBulk") IOException("offline") else null }

        val result = repository.clearAll()

        assertTrue(result is ArrResult.Ok && result.value is WriteOutcome.Queued)
        assertTrue(repository.cachedEntries().isEmpty())
        val pending = actionQueue.pending().single()
        assertEquals("DELETE", pending.method)
        assertEquals("api/v3/blocklist/bulk", pending.path)
        assertEquals(setOf(1, 2), repository.queuedRemovedIds().first())
    }

    @Test
    fun `clearAll with an empty cache is a no-op success`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))

        val result = repository.clearAll()

        assertTrue(result is ArrResult.Ok && result.value == WriteOutcome.Synced)
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `non-retryable removals surface instead of queueing`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        repository.refresh()
        fake.error = { op ->
            if (op == "Blocklist.delete") HttpException(Response.error<Any>(403, "".toResponseBody())) else {
                null
            }
        }

        val result = repository.removeEntry(1)

        assertTrue(result is ArrResult.Err && result.error is ArrError.Auth)
        assertTrue(actionQueue.pending().isEmpty())
        assertEquals(1, repository.cachedEntries().size)
    }

    @Test
    fun `queuedRemovedIds projects single deletes and bulk bodies for this server`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        fake.blocklistStore[2] = entry(2)
        repository.refresh()
        fake.error = { op ->
            if (op == "Blocklist.delete" || op == "Blocklist.deleteBulk") IOException("offline") else {
                null
            }
        }
        repository.removeEntry(2)
        repository.clearAll()

        assertEquals(setOf(1, 2), repository.queuedRemovedIds().first())
        assertEquals(2, repository.queuedCount().first())
    }

    @Test
    fun `foreign queued actions are ignored`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        actionQueue.enqueue(
            QueuedAction(
                actionId = dev.appolon.homelab.arr.core.ActionId("foreign"),
                serverId = ServerId("other"),
                appType = dev.appolon.homelab.arr.core.AppType.SONARR,
                method = "DELETE",
                path = "api/v3/blocklist/9",
                description = "foreign",
                enqueuedAtMillis = 0,
            ),
        )

        assertTrue(repository.queuedRemovedIds().first().isEmpty())
        assertEquals(0, repository.queuedCount().first())
    }
}
