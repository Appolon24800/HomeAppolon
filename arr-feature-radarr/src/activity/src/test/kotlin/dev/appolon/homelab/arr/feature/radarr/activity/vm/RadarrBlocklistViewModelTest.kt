package dev.appolon.homelab.arr.feature.radarr.activity.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.activity.data.ACTIVITY_PAGE_SIZE
import dev.appolon.homelab.arr.feature.radarr.activity.data.RadarrBlocklistRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrBlocklistViewModelTest {

    private val serverId = ServerId("server-1")
    private val otherServer = ServerId("server-2")
    private lateinit var fake: FakeRadarrQueue
    private lateinit var store: FakeEntityStore
    private val signals = MutableSharedFlow<ServerId>(extraBufferCapacity = 8)

    private fun viewModel(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): RadarrBlocklistViewModel =
        RadarrBlocklistViewModel(
            serverId = serverId,
            repository = RadarrBlocklistRepository(
                serverId = serverId,
                store = store,
                blocklistApi = fake,
                actionQueue = ActionQueueImpl(store),
                ioContext = StandardTestDispatcher(scheduler),
            ),
            refreshSignals = signals,
        )

    private fun entry(id: Int) = BlocklistResource(
        id = id,
        movieId = id,
        sourceTitle = "Release.$id",
        date = Instant.ofEpochSecond(1_800_000_000L - id * 3_600L).toString(),
        message = "Rejected $id",
        movie = MovieResource(id = id, title = "Movie $id"),
    )

    @Before
    fun setUp() {
        fake = FakeRadarrQueue()
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads content through the LCE states`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklistItems += entry(1)
        fake.blocklistItems += entry(2)
        val vm = viewModel(testScheduler)
        assertEquals(LceState.Loading, vm.lce.value)

        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrBlocklistContent>).data
        assertEquals(listOf(1, 2), content.entries.map { it.id })
        assertEquals("Rejected 1", content.entries.first { it.id == 1 }.reason)
    }

    @Test
    fun `empty blocklist renders the empty state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertEquals(LceState.Empty, vm.lce.value)
    }

    @Test
    fun `initial load failure with no cache renders the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.errorFor = { op -> if (op == "blocklist") IOException("offline") else null }
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
        assertTrue((vm.lce.value as LceState.Error).error is ArrError.Network)
    }

    @Test
    fun `online remove reports the synced notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklistItems += entry(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.removeEntry(vm.state.value.entries.single())
        advanceUntilIdle()

        assertEquals(BlocklistNotice.REMOVED, vm.state.value.notice)
        assertTrue(vm.state.value.entries.isEmpty())
    }

    @Test
    fun `offline remove shows the queued notice and pending count`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklistItems += entry(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.errorFor = { op -> if (op == "removeBlocklist") IOException("offline") else null }

        vm.removeEntry(vm.state.value.entries.single())
        advanceUntilIdle()

        assertEquals(BlocklistNotice.REMOVE_QUEUED, vm.state.value.notice)
        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals(1, vm.state.value.pendingActions)
    }

    @Test
    fun `offline clear all queues the bulk removal`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklistItems += entry(1)
        fake.blocklistItems += entry(2)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.errorFor = { op -> if (op == "removeBlocklistBulk") IOException("offline") else null }

        vm.clearAll()
        advanceUntilIdle()

        assertEquals(BlocklistNotice.CLEAR_QUEUED, vm.state.value.notice)
        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals(1, vm.state.value.pendingActions)
    }

    @Test
    fun `loadMore appends the next server page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        repeat(ACTIVITY_PAGE_SIZE + 1) { index -> fake.blocklistItems += entry(index + 1) }
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        assertEquals(ACTIVITY_PAGE_SIZE, vm.state.value.entries.size)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(ACTIVITY_PAGE_SIZE + 1, vm.state.value.entries.size)
    }

    @Test
    fun `live signals for this server refresh, others do not`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklistItems += entry(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.entries.size)

        fake.blocklistItems += entry(2)
        signals.tryEmit(otherServer)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, vm.state.value.entries.size)

        signals.tryEmit(serverId)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1, 2), vm.state.value.entries.map { it.id })
    }
}

/** Keeps a StateFlow hot for the duration of the test. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
