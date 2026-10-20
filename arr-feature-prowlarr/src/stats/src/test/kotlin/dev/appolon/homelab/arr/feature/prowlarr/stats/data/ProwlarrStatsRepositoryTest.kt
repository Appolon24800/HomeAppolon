package dev.appolon.homelab.arr.feature.prowlarr.stats.data

import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrSearch
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatistics
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeEntityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ProwlarrStatsRepositoryTest {

    private val serverId = ServerId("s1")
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var fake: FakeProwlarrSearch
    private lateinit var store: FakeEntityStore
    private var time = 10_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake = FakeProwlarrSearch()
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository() = ProwlarrStatsRepository(
        serverId = serverId,
        store = store,
        statsApi = { start, end -> fake.getIndexerStats(start, end, null, null, null) },
        now = { time },
        io = dispatcher,
    )

    private fun seed(resource: IndexerStatsResource = IndexerStatsResource()) {
        fake.indexerStats = resource
    }

    @Test
    fun `fetch stores the window and skips network inside the ttl`() = runTest(dispatcher) {
        seed(IndexerStatsResource(indexers = listOf(IndexerStatistics(indexerId = 1))))
        val repo = repository()
        val first = repo.stats(StatsPeriod.WEEK)
        assertTrue(first is ArrResult.Ok)
        assertEquals(1, fake.calls.count { it.operation == "getIndexerStats" })

        time += 1_000
        val second = repo.stats(StatsPeriod.WEEK)
        assertTrue(second is ArrResult.Ok)
        assertEquals(1, fake.calls.count { it.operation == "getIndexerStats" })
    }

    @Test
    fun `periods cache independently`() = runTest(dispatcher) {
        seed()
        val repo = repository()
        repo.stats(StatsPeriod.WEEK)
        repo.stats(StatsPeriod.DAY)
        val weekCalls = fake.calls.filter { it.operation == "getIndexerStats" }
        assertEquals(2, weekCalls.size)
        assertEquals(
            StatsPeriod.WEEK.window(java.time.Instant.ofEpochMilli(time)).first,
            weekCalls[0].arguments["startDate"],
        )
        assertEquals(
            StatsPeriod.DAY.window(java.time.Instant.ofEpochMilli(time)).first,
            weekCalls[1].arguments["startDate"],
        )
    }

    @Test
    fun `stale cache returns when a refresh fails`() = runTest(dispatcher) {
        seed(IndexerStatsResource(indexers = listOf(IndexerStatistics(indexerId = 7))))
        val repo = repository()
        repo.stats(StatsPeriod.MONTH)
        fake.errorStatus = { 500 }
        time += ProwlarrStatsRepository.STATS_TTL_MILLIS + 1

        val result = repo.stats(StatsPeriod.MONTH)
        assertTrue(result is ArrResult.Ok)
        assertEquals(7, (result as ArrResult.Ok).value.indexers?.first()?.indexerId)
    }

    @Test
    fun `fetch error surfaces when nothing is cached`() = runTest(dispatcher) {
        fake.errorStatus = { 401 }
        val repo = repository()
        val result = repo.stats(StatsPeriod.WEEK)
        assertTrue(result is ArrResult.Err)
        val error = (result as ArrResult.Err).error
        assertTrue(error is dev.appolon.homelab.arr.core.ArrError.Auth)
    }

    @Test
    fun `refresh forces the network inside the ttl`() = runTest(dispatcher) {
        seed()
        val repo = repository()
        repo.stats(StatsPeriod.WEEK)
        val result = repo.refresh(StatsPeriod.WEEK)
        assertTrue(result is ArrResult.Ok)
        assertEquals(2, fake.calls.count { it.operation == "getIndexerStats" })
    }

    @Test
    fun `stats cache uses its own entity type`() {
        assertEquals("prowlarr.indexerstats", ProwlarrStatsRepository.STATS_TYPE.value)
    }
}
