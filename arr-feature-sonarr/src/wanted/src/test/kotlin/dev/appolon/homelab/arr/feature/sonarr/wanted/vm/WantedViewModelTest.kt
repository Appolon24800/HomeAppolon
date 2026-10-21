package dev.appolon.homelab.arr.feature.sonarr.wanted.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrDiscovery
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.wanted.FakeWantedActionQueue
import dev.appolon.homelab.arr.feature.sonarr.wanted.FakeWantedEntityStore
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.SonarrWantedRepository
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.WantedCounts
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.WantedTab
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class WantedViewModelTest {

    private val main = UnconfinedTestDispatcher()

    private lateinit var fake: FakeSonarrDiscovery
    private lateinit var store: FakeWantedEntityStore
    private lateinit var queue: FakeWantedActionQueue
    private lateinit var vm: WantedViewModel

    private val commandCalls = mutableListOf<Triple<String, Int?, List<Int>?>>()
    private var commandResult: ArrResult<Unit> = ArrResult.ok(Unit)

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun episode(index: Int) = EpisodeResource(
        id = index + 1,
        seriesId = index / 10 + 1,
        seasonNumber = index % 2 + 1,
        episodeNumber = index + 1,
        title = "Episode ${index + 1}",
        airDateUtc = "2026-01-%02dT00:00:00Z".format(index % 28 + 1),
        monitored = true,
        series = SeriesResource(
            id = index / 10 + 1,
            title = "Series %02d".format(index / 10 + 1),
            sortTitle = "series %02d".format(index / 10 + 1),
        ),
    )

    private fun newFakes(episodeCount: Int = 120) {
        fake = FakeSonarrDiscovery()
        store = FakeWantedEntityStore()
        queue = FakeWantedActionQueue()
        commandCalls.clear()
        repeat(episodeCount) { index -> fake.missingEpisodes += episode(index) }
        // A small, separate cutoff list proves the two tabs stay independent.
        repeat(5) { index -> fake.cutoffEpisodes += episode(index).copy(id = 900 + index) }
    }

    private fun newVm(): WantedViewModel {
        val repository = SonarrWantedRepository(
            serverId = ServerId("s1"),
            store = store,
            missingApi = fake.missingApi,
            cutoffApi = fake.cutoff,
            commandRunner = { name, seriesId, episodeIds ->
                commandCalls += Triple(name, seriesId, episodeIds)
                commandResult
            },
            actionQueue = queue,
            io = main,
        )
        return WantedViewModel(repository)
    }

    private fun runVm(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest {
        vm = newVm()
        val job1 = launch { vm.lce.collect {} }
        val job2 = launch { vm.counts.collect {} }
        advanceUntilIdle()
        block()
        advanceUntilIdle()
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun `initial load renders missing content with both totals`() {
        newFakes()
        runVm {
            val lce = vm.lce.value
            assertTrue(lce is LceState.Content)
            assertEquals(WantedTab.MISSING, vm.state.value.tab)
            assertEquals(100, vm.state.value.active.episodes.size)
            assertEquals(120, vm.state.value.active.totalRecords)
            assertEquals(WantedCounts(missing = 120, cutoff = 5), vm.counts.value)
        }
    }

    @Test
    fun `switching tabs keeps cutoff data warm`() {
        newFakes()
        runVm {
            vm.selectTab(WantedTab.CUTOFF)
            val active = vm.state.value.active
            assertEquals(5, active.episodes.size)
            assertTrue(vm.lce.value is LceState.Content)
        }
    }

    @Test
    fun `loadMore appends the remaining server pages`() {
        newFakes()
        runVm {
            vm.loadMore()
            val active = vm.state.value.active
            assertEquals(120, active.episodes.size)
            assertTrue(active.endReached)
            assertFalse(active.loadingMore)
        }
    }

    @Test
    fun `failed page append blocks auto retry until refresh`() {
        newFakes()
        runVm {
            fake.error = { operation ->
                if (operation == "Missing.list") {
                    HttpException(Response.error<Any>(500, "{}".toResponseBody()))
                } else {
                    null
                }
            }
            vm.loadMore()
            assertTrue(vm.state.value.active.loadMoreFailed)
            vm.loadMore()
            assertEquals(0, commandCalls.size)
            fake.error = { null }
            vm.refresh()
            val active = vm.state.value.active
            assertFalse(active.loadMoreFailed)
            assertEquals(100, active.episodes.size)
        }
    }

    @Test
    fun `season filter narrows the presentation`() {
        newFakes()
        runVm {
            vm.toggleSeason("1")
            val presented = vm.state.value.presentation.all
            assertTrue(presented.isNotEmpty())
            assertTrue(presented.all { it.seasonNumber == 1 })
            vm.toggleSeason("1")
            assertEquals(100, vm.state.value.presentation.all.size)
        }
    }

    @Test
    fun `monitored filter cycles unset on and off`() {
        newFakes()
        runVm {
            // The wanted endpoints only list monitored episodes, so the "on"
            // side keeps everything and the "off" side empties the list.
            vm.toggleMonitored()
            assertTrue(vm.state.value.presentation.all.all { it.monitored })
            vm.toggleMonitored()
            assertTrue(vm.state.value.presentation.all.none { it.monitored })
            vm.toggleMonitored()
            assertEquals(100, vm.state.value.presentation.all.size)
        }
    }

    @Test
    fun `per episode search dispatches and reports progress`() {
        newFakes()
        runVm {
            vm.searchEpisode(5)
            val state = vm.state.value
            assertTrue(state.searchingEpisodeIds.isEmpty())
            assertEquals(
                listOf(Triple("EpisodeSearch", null, listOf(5))),
                commandCalls,
            )
            assertTrue(state.message is WantedMessage.EpisodeSearchStarted)
            vm.consumeMessage()
            assertEquals(null, vm.state.value.message)
        }
    }

    @Test
    fun `per series search dispatches SeriesSearch`() {
        newFakes()
        runVm {
            vm.searchSeries(3)
            assertTrue(vm.state.value.searchingSeriesIds.isEmpty())
            assertEquals(listOf(Triple("SeriesSearch", 3, null)), commandCalls)
            assertTrue(vm.state.value.message is WantedMessage.SeriesSearchStarted)
        }
    }

    @Test
    fun `offline per episode search queues and badges the row`() {
        newFakes()
        commandResult = ArrResult.err(ArrError.Offline())
        runVm {
            vm.searchEpisode(5)
            val state = vm.state.value
            assertEquals(setOf(5), state.queuedEpisodeIds)
            assertTrue(state.message is WantedMessage.SearchQueued)
            assertEquals(1, queue.pending().size)
        }
    }

    @Test
    fun `offline per series search queues and badges the series`() {
        newFakes()
        commandResult = ArrResult.err(ArrError.Offline())
        runVm {
            vm.searchSeries(5)
            val state = vm.state.value
            assertEquals(setOf(5), state.queuedSeriesIds)
            assertTrue(state.message is WantedMessage.SearchQueued)
        }
    }

    @Test
    fun `bulk search targets only the filtered presentation`() {
        newFakes()
        runVm {
            vm.toggleSeason("1")
            vm.searchAll()
            val expected = vm.state.value.presentation.all.map { it.id }
            assertEquals(listOf(Triple("EpisodeSearch", null, expected)), commandCalls)
            assertTrue(vm.state.value.message is WantedMessage.EpisodeSearchStarted)
            assertFalse(vm.state.value.bulkSearching)
        }
    }

    @Test
    fun `empty library renders the empty state`() {
        newFakes(episodeCount = 0)
        runVm {
            assertTrue(vm.lce.value is LceState.Empty)
        }
    }

    @Test
    fun `refresh failure with empty cache renders the error state`() {
        newFakes()
        fake.error = { HttpException(Response.error<Any>(500, "{}".toResponseBody())) }
        runVm {
            assertTrue(vm.lce.value is LceState.Error)
            assertTrue(vm.state.value.active.episodes.isEmpty())
        }
    }
}
