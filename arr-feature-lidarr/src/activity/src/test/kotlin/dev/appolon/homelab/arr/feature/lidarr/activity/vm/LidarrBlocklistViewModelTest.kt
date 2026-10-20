package dev.appolon.homelab.arr.feature.lidarr.activity.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.BlocklistResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.activity.data.LidarrBlocklistRepository
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
class LidarrBlocklistViewModelTest {

    private val serverId = ServerId("server-1")
    private val otherServer = ServerId("server-2")
    private lateinit var fake: FakeLidarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private val signals = MutableSharedFlow<ServerId>(extraBufferCapacity = 8)

    private fun viewModel(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): LidarrBlocklistViewModel =
        LidarrBlocklistViewModel(
            serverId = serverId,
            repository = LidarrBlocklistRepository(
                serverId = serverId,
                store = store,
                blocklistApi = fake,
                actionQueue = actionQueue,
                ioContext = StandardTestDispatcher(scheduler),
            ),
            refreshSignals = signals,
        )

    private fun entry(id: Int, date: Instant = Instant.ofEpochSecond(1_800_000_000L - id * 3_600L)) =
        BlocklistResource(
            id = id,
            artistId = id,
            sourceTitle = "Release.$id",
            date = date.toString(),
            message = "Quality out of profile",
        )

    @Before
    fun setUp() {
        fake = FakeLidarrQueue()
        store = FakeEntityStore()
        actionQueue = ActionQueueImpl(store)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads entries through the LCE states newest first with reasons`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        fake.blocklist[2] = entry(2, date = Instant.parse("2026-10-19T00:00:00Z"))
        val vm = viewModel(testScheduler)

        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrBlocklistContent>).data
        assertEquals(listOf(1, 2), content.entries.map { it.id })
        assertEquals("Quality out of profile", content.entries.first().reason)
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
    fun `removeEntry online removes the row and posts the notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.removeEntry(vm.state.value.entries.single())
        advanceUntilIdle()

        assertEquals(LidarrBlocklistNotice.REMOVED, vm.state.value.notice)
        assertTrue(vm.state.value.entries.isEmpty())
        assertTrue(fake.blocklist.isEmpty())
    }

    @Test
    fun `removeEntry offline queues the delete and clears the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { if (it == "deleteBlocklist") IOException("offline") else null }

        vm.removeEntry(vm.state.value.entries.single())
        advanceUntilIdle()

        assertEquals(LidarrBlocklistNotice.REMOVE_QUEUED, vm.state.value.notice)
        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals("api/v1/blocklist/1", actionQueue.pending().single().path)
    }

    @Test
    fun `clearAll online empties the list`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        fake.blocklist[2] = entry(2, date = Instant.parse("2026-10-19T00:00:00Z"))
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.clearAll()
        advanceUntilIdle()

        assertEquals(LidarrBlocklistNotice.CLEARED, vm.state.value.notice)
        assertTrue(vm.state.value.entries.isEmpty())
        assertTrue(fake.blocklist.isEmpty())
    }

    @Test
    fun `clearAll offline queues one bulk delete`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        fake.blocklist[2] = entry(2, date = Instant.parse("2026-10-19T00:00:00Z"))
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { if (it == "deleteBlocklistBulk") IOException("offline") else null }

        vm.clearAll()
        advanceUntilIdle()

        assertEquals(LidarrBlocklistNotice.CLEAR_QUEUED, vm.state.value.notice)
        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals("api/v1/blocklist/bulk", actionQueue.pending().single().path)
    }

    @Test
    fun `loadMore appends the next page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        repeat(55) { index -> fake.blocklist[index + 1] = entry(index + 1) }
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(55, vm.state.value.entries.size)
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `refresh signals for this server trigger a debounced refresh`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.blocklist[2] = entry(2, date = Instant.parse("2026-10-20T11:00:00Z"))

        signals.tryEmit(serverId)
        advanceTimeBy(1_000)
        runCurrent()

        val content = (vm.lce.value as LceState.Content<LidarrBlocklistContent>).data
        assertEquals(setOf(1, 2), content.entries.map { it.id }.toSet())
    }

    @Test
    fun `refresh signals for other servers are ignored`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklist[1] = entry(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.blocklist[2] = entry(2, date = Instant.parse("2026-10-20T11:00:00Z"))

        signals.tryEmit(otherServer)
        advanceTimeBy(1_000)
        runCurrent()

        val content = (vm.lce.value as LceState.Content<LidarrBlocklistContent>).data
        assertEquals(listOf(1), content.entries.map { it.id })
    }
}

/** Keeps a StateFlow hot for the duration of the test. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
