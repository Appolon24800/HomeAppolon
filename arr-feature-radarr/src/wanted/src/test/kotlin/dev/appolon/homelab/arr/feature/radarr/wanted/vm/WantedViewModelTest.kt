package dev.appolon.homelab.arr.feature.radarr.wanted.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrDiscovery
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieCommandRunner
import dev.appolon.homelab.arr.feature.radarr.wanted.FakeWantedActionQueue
import dev.appolon.homelab.arr.feature.radarr.wanted.FakeWantedEntityStore
import dev.appolon.homelab.arr.feature.radarr.wanted.data.RadarrWantedRepository
import dev.appolon.homelab.arr.feature.radarr.wanted.data.WantedCounts
import dev.appolon.homelab.arr.feature.radarr.wanted.data.WantedFilters
import dev.appolon.homelab.arr.feature.radarr.wanted.data.WantedTab
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

    private lateinit var fake: FakeRadarrDiscovery
    private lateinit var store: FakeWantedEntityStore
    private lateinit var queue: FakeWantedActionQueue
    private lateinit var vm: WantedViewModel

    private val commandCalls = mutableListOf<Pair<String, List<Int>?>>()
    private var commandResult: ArrResult<Unit> = ArrResult.ok(Unit)

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newFakes(movieCount: Int = 120) {
        fake = FakeRadarrDiscovery()
        store = FakeWantedEntityStore()
        queue = FakeWantedActionQueue()
        commandCalls.clear()
        repeat(movieCount) { index ->
            fake.movies += MovieResource(
                id = index + 1,
                title = "Title %03d".format(index + 1),
                sortTitle = "title %03d".format(index + 1),
                monitored = (index + 1) % 2 == 0,
                qualityProfileId = if ((index + 1) % 3 == 0) 2 else 1,
            )
        }
    }

    private fun newVm(): WantedViewModel {
        val repository = RadarrWantedRepository(
            serverId = ServerId("s1"),
            store = store,
            missingApi = fake,
            cutoffApi = fake,
            qualityProfileApi = {
                listOf(
                    QualityProfileResource(id = 1, name = "HD"),
                    QualityProfileResource(id = 2, name = "UHD"),
                )
            },
            commandRunner = MovieCommandRunner { command, ids ->
                commandCalls += command to ids
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
            assertEquals(100, vm.state.value.active.movies.size)
            assertEquals(120, vm.state.value.active.totalRecords)
            assertEquals(WantedCounts(missing = 120, cutoff = 120), vm.counts.value)
        }
    }

    @Test
    fun `switching tabs keeps cutoff data warm`() {
        newFakes()
        runVm {
            vm.selectTab(WantedTab.CUTOFF)
            val active = vm.state.value.active
            assertEquals(100, active.movies.size)
            assertTrue(vm.lce.value is LceState.Content)
        }
    }

    @Test
    fun `loadMore appends the remaining server pages`() {
        newFakes()
        runVm {
            vm.loadMore()
            val active = vm.state.value.active
            assertEquals(120, active.movies.size)
            assertTrue(active.endReached)
            assertFalse(active.loadingMore)
        }
    }

    @Test
    fun `failed page append blocks auto retry until refresh`() {
        newFakes()
        runVm {
            // The fake funnels missing() through cutoff(), so both wanted
            // endpoints report the operation name "cutoff".
            fake.errorFor = { operation ->
                if (operation == "cutoff") {
                    HttpException(Response.error<Any>(500, "{}".toResponseBody()))
                } else {
                    null
                }
            }
            vm.loadMore()
            assertTrue(vm.state.value.active.loadMoreFailed)
            vm.loadMore()
            assertEquals(0, commandCalls.size)
            fake.errorFor = { null }
            vm.refresh()
            val active = vm.state.value.active
            assertFalse(active.loadMoreFailed)
            assertEquals(100, active.movies.size)
        }
    }

    @Test
    fun `monitored filter narrows the presentation`() {
        newFakes()
        runVm {
            vm.toggleMonitored()
            val presented = vm.state.value.presentation.all
            assertTrue(presented.isNotEmpty())
            assertTrue(presented.all { it.monitored })
            vm.toggleMonitored()
            assertTrue(
                vm.state.value.presentation.all.none { it.monitored },
            )
        }
    }

    @Test
    fun `quality toggle adds and removes one profile`() {
        newFakes()
        runVm {
            vm.toggleQuality("2")
            assertTrue(
                vm.state.value.presentation.all.all { it.qualityProfileId == 2 },
            )
            vm.toggleQuality("2")
            assertEquals(100, vm.state.value.presentation.all.size)
            vm.clearFilters()
            assertEquals(100, vm.state.value.presentation.all.size)
        }
    }

    @Test
    fun `per item search dispatches and reports progress`() {
        newFakes()
        runVm {
            vm.searchMovie(5)
            val state = vm.state.value
            assertTrue(state.searchingIds.isEmpty())
            assertEquals(listOf("MoviesSearch" to listOf(5)), commandCalls)
            assertTrue(state.message is WantedMessage.SearchStarted)
            vm.consumeMessage()
            assertEquals(null, vm.state.value.message)
        }
    }

    @Test
    fun `offline per item search queues and badges the row`() {
        newFakes()
        commandResult = ArrResult.err(ArrError.Offline())
        runVm {
            vm.searchMovie(5)
            val state = vm.state.value
            assertEquals(setOf(5), state.queuedIds)
            assertTrue(state.message is WantedMessage.SearchQueued)
            assertEquals(1, queue.pending().size)
        }
    }

    @Test
    fun `bulk search targets only the filtered presentation`() {
        newFakes()
        runVm {
            vm.toggleMonitored()
            vm.searchAll()
            val expected = vm.state.value.presentation.all.map { it.id }
            assertEquals(listOf("MoviesSearch" to expected), commandCalls)
            assertTrue(vm.state.value.message is WantedMessage.SearchStarted)
        }
    }

    @Test
    fun `empty library renders the empty state`() {
        newFakes(movieCount = 0)
        runVm {
            assertTrue(vm.lce.value is LceState.Empty)
        }
    }

    @Test
    fun `refresh failure with empty cache renders the error state`() {
        newFakes()
        fake.errorFor = { HttpException(Response.error<Any>(500, "{}".toResponseBody())) }
        runVm {
            assertTrue(vm.lce.value is LceState.Error)
            assertTrue(vm.state.value.active.movies.isEmpty())
        }
    }
}
