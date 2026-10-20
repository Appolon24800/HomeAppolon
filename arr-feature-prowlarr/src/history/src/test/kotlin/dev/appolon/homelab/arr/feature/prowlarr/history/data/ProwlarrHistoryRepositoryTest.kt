package dev.appolon.homelab.arr.feature.prowlarr.history.data

import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrSearch
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.SortDirection
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeActionQueue
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeEntityStore
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.WriteOutcome
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProwlarrHistoryRepositoryTest {

    private val serverId = ServerId("s1")
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var search: FakeProwlarrSearch
    private lateinit var indexers: FakeProwlarrIndexers
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        search = FakeProwlarrSearch()
        indexers = FakeProwlarrIndexers()
        store = FakeEntityStore()
        queue = FakeActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository() = ProwlarrHistoryRepository(
        serverId = serverId,
        store = store,
        historyApi = { page, pageSize, direction, indexerIds ->
            search.getHistory(page, pageSize, "date", direction, null, null, null, indexerIds)
        },
        indexerListApi = { indexers.getIndexers() },
        commandApi = { search.createCommand(it) },
        actionQueue = queue,
        now = { 10_000L },
        io = dispatcher,
    )

    private fun event(id: Int, date: String = "2026-10-20T10:%02d:%02dZ".format(id / 60, id % 60)) =
        HistoryResource(id = id, indexerId = 1, date = date)

    @Test
    fun `page one replaces the cache and later pages append without duplicates`() =
        runTest(dispatcher) {
        val repo = repository()
        search.history += (1..120).map { event(it) }
        repo.loadPage(1)
        assertEquals(50, repo.cachedHistory().size)

        repo.loadPage(2)
        val afterTwo = repo.cachedHistory()
        assertEquals(100, afterTwo.size)
        assertEquals(afterTwo.size, afterTwo.map { it.id }.distinct().size)

        repo.loadPage(3)
        assertEquals(120, repo.cachedHistory().size)

        // Re-loading a page replaces by id instead of duplicating rows.
        repo.loadPage(2)
        assertEquals(120, repo.cachedHistory().size)
    }

    @Test
    fun `indexer filter rides the request and indexers share the sibling cache`() =
        runTest(dispatcher) {
            indexers.indexers[1] = IndexerResource(id = 1, name = "Alpha")
            indexers.indexers[2] = IndexerResource(id = 2, name = "Beta")
            val repo = repository()
            repo.loadPage(1, indexerIds = listOf(2))
            val call = search.calls.first { it.operation == "getHistory" }
            assertEquals(listOf(2), call.arguments["indexerIds"])

            repo.indexers(ttlMillis = 0)
            val cached = store.getAll(serverId, INDEXER_TYPE, IndexerResource.serializer())
            assertEquals(2, cached.size)
        }

    @Test
    fun `clear history runs the command and empties the cache`() = runTest(dispatcher) {
        search.history += listOf(event(1), event(2))
        val repo = repository()
        repo.loadPage(1)
        val outcome = repo.clearHistory()
        assertTrue(outcome is ArrResult.Ok)
        assertEquals(WriteOutcome.Synced, (outcome as ArrResult.Ok).value)
        assertTrue(repo.cachedHistory().isEmpty())
        val command = search.commands.values.single()
        assertEquals(ProwlarrHistoryRepository.CLEAR_HISTORY_COMMAND, command.name)
    }

    @Test
    fun `retryable clear failure queues with an optimistic clear`() = runTest(dispatcher) {
        search.history += listOf(event(1))
        val repo = repository()
        repo.loadPage(1)
        search.failure = { throw IOException("offline") }

        val outcome = repo.clearHistory()
        assertTrue(outcome is ArrResult.Ok)
        val queuedOutcome = (outcome as ArrResult.Ok).value as WriteOutcome.Queued
        assertTrue(repo.cachedHistory().isEmpty())
        val queued = queue.pending().single()
        assertEquals(queuedOutcome.actionId, queued.actionId)
        assertEquals("POST", queued.method)
        assertEquals("api/v1/command", queued.path)
        val expectedBody = ProwlarrHistoryRepository.CLEAR_HISTORY_COMMAND
        assertTrue(queued.bodyJson.orEmpty().contains(expectedBody))
        assertTrue(repo.clearQueued().first())
    }

    @Test
    fun `non-retryable clear failure returns the error`() = runTest(dispatcher) {
        val repo = repository()
        search.errorStatus = { 401 }
        val outcome = repo.clearHistory()
        assertTrue(outcome is ArrResult.Err)
        assertTrue(queue.pending().isEmpty())
        assertFalse(repo.clearQueued().first())
    }

    private companion object {
        private val INDEXER_TYPE = ProwlarrIndexersRepository.INDEXER_TYPE
    }
}
