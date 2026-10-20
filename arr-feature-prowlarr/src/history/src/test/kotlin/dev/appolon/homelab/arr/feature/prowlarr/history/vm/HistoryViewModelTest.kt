package dev.appolon.homelab.arr.feature.prowlarr.history.vm

import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrSearch
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.history.data.ProwlarrHistoryRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeActionQueue
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
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
class HistoryViewModelTest {

    private val serverId = ServerId("s1")
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var search: FakeProwlarrSearch
    private lateinit var indexers: FakeProwlarrIndexers
    private lateinit var repository: ProwlarrHistoryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        search = FakeProwlarrSearch()
        indexers = FakeProwlarrIndexers()
        indexers.indexers[1] = IndexerResource(id = 1, name = "Alpha")
        indexers.indexers[2] = IndexerResource(id = 2, name = "Beta")
        repository = ProwlarrHistoryRepository(
            serverId = serverId,
            store = FakeEntityStore(),
            historyApi = { page, pageSize, direction, indexerIds ->
                search.getHistory(page, pageSize, "date", direction, null, null, null, indexerIds)
            },
            indexerListApi = { indexers.getIndexers() },
            commandApi = { search.createCommand(it) },
            actionQueue = FakeActionQueue(),
            now = { 10_000L },
            io = dispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun seedHistory(count: Int) {
        search.history += (1..count).map { id ->
            HistoryResource(
                id = id,
                indexerId = if (id % 2 == 0) 2 else 1,
                date = "2026-10-${(id % 20 + 10).toString().padStart(2, '0')}T10:00:00Z",
                data = mapOf(
                    "query" to "query $id",
                    "elapsedTime" to "$id",
                    "cached" to if (id % 3 == 0) "1" else "0",
                    "queryResults" to "$id",
                ),
            )
        }
    }

    @Test
    fun `initial load presents the newest window`() = runTest(dispatcher) {
        seedHistory(30)
        val vm = HistoryViewModel(repository)
        backgroundScope.launch { vm.lce.collect {} }
        vm.viewModelScope.coroutineContext.cancelChildren()
        val state = vm.state.value
        assertTrue(state.loaded)
        assertEquals(30, state.rows.size)
        assertEquals(30, state.totalCount)
        assertTrue(state.endReached)
        assertEquals(2, state.indexers.size)
        val first = state.rows.first()
        assertEquals(19, first.id)
        assertEquals("Alpha", first.indexerName)
        assertTrue(first.cached != null)
    }

    @Test
    fun `query filter narrows the presented rows without refetching`() = runTest(dispatcher) {
        seedHistory(10)
        val vm = HistoryViewModel(repository)
        vm.viewModelScope.coroutineContext.cancelChildren()
        val callsBefore = search.calls.size
        vm.setQuery("query 7")
        val state = vm.state.value
        assertEquals(listOf(7), state.rows.map { it.id })
        assertEquals(callsBefore, search.calls.size)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `indexer filter reloads page one with the server-side filter`() = runTest(dispatcher) {
        seedHistory(10)
        val vm = HistoryViewModel(repository)
        vm.setIndexerFilter(2)
        val filteredCall = search.calls.filter { it.operation == "getHistory" }.last()
        assertEquals(listOf(2), filteredCall.arguments["indexerIds"])
        val state = vm.state.value
        assertEquals(5, state.rows.size)
        assertTrue(state.rows.all { it.event.indexerId == 2 })
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `load more pages until the window is exhausted`() = runTest(dispatcher) {
        seedHistory(120)
        val vm = HistoryViewModel(repository)
        assertFalse(vm.state.value.endReached)

        vm.loadMore()
        assertEquals(100, vm.state.value.rows.size)
        assertFalse(vm.state.value.endReached)

        vm.loadMore()
        assertEquals(120, vm.state.value.rows.size)
        assertTrue(vm.state.value.endReached)

        val pages = search.calls.filter { it.operation == "getHistory" }
            .map { it.arguments["page"] }
        assertEquals(listOf(1, 2, 3), pages)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `clear history succeeds and reloads an empty window`() = runTest(dispatcher) {
        seedHistory(5)
        val vm = HistoryViewModel(repository)
        backgroundScope.launch { vm.lce.collect {} }
        vm.requestClear()
        assertTrue(vm.state.value.confirmClear)

        search.history.clear()
        vm.confirmClear()
        val state = vm.state.value
        assertEquals(HistoryMessage.Cleared, state.message)
        assertTrue(state.rows.isEmpty())
        assertTrue(vm.lce.value is dev.appolon.homelab.arr.ui.lce.LceState.Empty)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `offline clear queues optimistically and flags the pending action`() = runTest(dispatcher) {
        seedHistory(5)
        val vm = HistoryViewModel(repository)

        search.failure = { throw IOException("offline") }
        vm.requestClear()
        vm.confirmClear()

        val state = vm.state.value
        assertEquals(HistoryMessage.ClearQueued, state.message)
        assertTrue(state.clearQueued)
        assertTrue(state.rows.isEmpty())
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `empty server history renders the empty state`() = runTest(dispatcher) {
        val vm = HistoryViewModel(repository)
        backgroundScope.launch { vm.lce.collect {} }
        vm.viewModelScope.coroutineContext.cancelChildren()
        assertTrue(vm.state.value.loaded)
        assertTrue(vm.lce.value is dev.appolon.homelab.arr.ui.lce.LceState.Empty)
    }
}
