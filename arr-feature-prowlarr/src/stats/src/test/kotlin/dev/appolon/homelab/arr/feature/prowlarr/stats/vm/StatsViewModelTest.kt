package dev.appolon.homelab.arr.feature.prowlarr.stats.vm

import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrSearch
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatistics
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeEntityStore
import dev.appolon.homelab.arr.feature.prowlarr.stats.data.ProwlarrStatsRepository
import dev.appolon.homelab.arr.feature.prowlarr.stats.data.StatsPeriod
import dev.appolon.homelab.arr.ui.lce.LceState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val serverId = ServerId("s1")
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var fake: FakeProwlarrSearch

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake = FakeProwlarrSearch()
        fake.indexerStats = IndexerStatsResource(
            indexers = listOf(
                IndexerStatistics(indexerId = 1, indexerName = "Alpha", numberOfQueries = 10),
            ),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(now: () -> Long = { 10_000L }) = ProwlarrStatsRepository(
        serverId = serverId,
        store = FakeEntityStore(),
        statsApi = { start, end -> fake.getIndexerStats(start, end, null, null, null) },
        now = now,
        io = dispatcher,
    )

    @Test
    fun `loads the week window by default`() = runTest(dispatcher) {
        val vm = StatsViewModel(repository())
        vm.viewModelScope.coroutineContext.cancelChildren()
        val state = vm.state.value
        assertTrue(state.loaded)
        assertEquals(StatsPeriod.WEEK, state.period)
        assertEquals(1, state.indexerRows.size)
        assertEquals(10, state.totals.queries)
        val call = fake.calls.first { it.operation == "getIndexerStats" }
        assertEquals(
            StatsPeriod.WEEK.window(java.time.Instant.ofEpochMilli(10_000L)).first,
            call.arguments["startDate"],
        )
    }

    @Test
    fun `switching period queries the new window`() = runTest(dispatcher) {
        val vm = StatsViewModel(repository())
        vm.viewModelScope.coroutineContext.cancelChildren()
        vm.setPeriod(StatsPeriod.DAY)
        vm.viewModelScope.coroutineContext.cancelChildren()
        val dayCall = fake.calls.filter { it.operation == "getIndexerStats" }.last()
        assertEquals(
            StatsPeriod.DAY.window(java.time.Instant.ofEpochMilli(10_000L)).first,
            dayCall.arguments["startDate"],
        )
        assertEquals(StatsPeriod.DAY, vm.state.value.period)
    }

    @Test
    fun `fetch failure shows the error state`() = runTest(dispatcher) {
        fake.errorStatus = { 503 }
        val vm = StatsViewModel(repository())
        backgroundScope.launch { vm.lce.collect {} }
        vm.viewModelScope.coroutineContext.cancelChildren()
        assertTrue(vm.state.value.loadError != null)
        assertTrue(vm.lce.value is LceState.Error)
    }

    @Test
    fun `all-zero counters render as empty not error`() = runTest(dispatcher) {
        fake.indexerStats = IndexerStatsResource()
        val vm = StatsViewModel(repository())
        backgroundScope.launch { vm.lce.collect {} }
        vm.viewModelScope.coroutineContext.cancelChildren()
        assertTrue(vm.state.value.loaded)
        assertTrue(vm.lce.value is LceState.Empty)
    }

    @Test
    fun `refresh forces a second fetch`() = runTest(dispatcher) {
        val vm = StatsViewModel(repository())
        vm.viewModelScope.coroutineContext.cancelChildren()
        vm.refresh()
        vm.viewModelScope.coroutineContext.cancelChildren()
        assertEquals(2, fake.calls.count { it.operation == "getIndexerStats" })
        assertTrue(vm.state.value.refreshing.not())
    }
}
