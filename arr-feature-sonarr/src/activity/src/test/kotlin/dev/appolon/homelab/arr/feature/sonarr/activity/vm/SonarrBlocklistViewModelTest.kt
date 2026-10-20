package dev.appolon.homelab.arr.feature.sonarr.activity.vm

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.BlocklistResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.sonarr.activity.FakeActivityEntityStore
import dev.appolon.homelab.arr.feature.sonarr.activity.data.ACTIVITY_PAGE_SIZE
import dev.appolon.homelab.arr.feature.sonarr.activity.data.SonarrBlocklistRepository
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SonarrBlocklistViewModelTest {

    private val serverId = ServerId("server-1")
    private val otherServer = ServerId("server-2")
    private lateinit var fake: FakeSonarrQueue
    private lateinit var store: FakeActivityEntityStore
    private val signals = MutableSharedFlow<ServerId>(extraBufferCapacity = 8)

    private fun viewModel(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): SonarrBlocklistViewModel =
        SonarrBlocklistViewModel(
            serverId = serverId,
            repository = SonarrBlocklistRepository(
                serverId = serverId,
                store = store,
                blocklistApi = fake.blocklist,
                actionQueue = ActionQueueImpl(store),
                ioContext = StandardTestDispatcher(scheduler),
            ),
            refreshSignals = signals,
        )

    private fun entry(id: Int) = BlocklistResource(
        id = id,
        seriesId = id,
        sourceTitle = "Release.$id",
        date = Instant.ofEpochSecond(1_800_000_000L - id * 3_600L).toString(),
        message = "Rejected $id",
        series = SeriesResource(title = "Series $id"),
    )

    @Before
    fun setUp() {
        fake = FakeSonarrQueue()
        store = FakeActivityEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads content through the LCE states`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        fake.blocklistStore[2] = entry(2)
        val vm = viewModel(testScheduler)
        assertEquals(LceState.Loading, vm.lce.value)

        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrBlocklistContent>).data
        assertEquals(listOf(1, 2), content.entries.map { it.id })
        assertEquals("Rejected 1", content.entries.first { it.id == 1 }.reason)
        assertEquals("Series 1", content.entries.first { it.id == 1 }.title)
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
        fake.error = { op -> if (op == "Blocklist.list") IOException("offline") else null }
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
        assertTrue((vm.lce.value as LceState.Error).error is ArrError.Network)
    }

    @Test
    fun `online remove reports the synced notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.removeEntry(vm.state.value.entries.single())
        advanceUntilIdle()

        assertEquals(BlocklistNotice.REMOVED, vm.state.value.notice)
        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals(0, vm.state.value.pendingActions)
    }

    @Test
    fun `offline remove queues optimistically and badges nothing left behind`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "Blocklist.delete") IOException("offline") else null }

        vm.removeEntry(vm.state.value.entries.single())
        advanceUntilIdle()

        assertEquals(BlocklistNotice.REMOVE_QUEUED, vm.state.value.notice)
        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals(1, vm.state.value.pendingActions)
    }

    @Test
    fun `online clear all empties the list`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        fake.blocklistStore[2] = entry(2)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.clearAll()
        advanceUntilIdle()

        assertEquals(BlocklistNotice.CLEARED, vm.state.value.notice)
        assertTrue(vm.state.value.entries.isEmpty())
    }

    @Test
    fun `loadMore appends the next server page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        repeat(ACTIVITY_PAGE_SIZE + 3) { index ->
            fake.blocklistStore[index + 1] = entry(index + 1)
        }
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        assertEquals(ACTIVITY_PAGE_SIZE, vm.state.value.entries.size)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(ACTIVITY_PAGE_SIZE + 3, vm.state.value.entries.size)
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `live signals for this server refresh, others do not`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.entries.size)

        fake.blocklistStore[2] = entry(2)
        signals.tryEmit(otherServer)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, vm.state.value.entries.size)

        signals.tryEmit(serverId)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1, 2), vm.state.value.entries.map { it.id })
    }

    @Test
    fun `consumeWriteError clears a surfaced write failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.blocklistStore[1] = entry(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op ->
            if (op == "Blocklist.delete") {
                retrofit2.HttpException(
                    retrofit2.Response.error<Any>(403, okhttp3.ResponseBody.create(null, "")),
                )
            } else {
                null
            }
        }

        vm.removeEntry(vm.state.value.entries.single())
        advanceUntilIdle()

        assertTrue(vm.state.value.writeError is ArrError.Auth)
        vm.consumeWriteError()
        assertEquals(null, vm.state.value.writeError)
        assertFalse(vm.state.value.notice == BlocklistNotice.REMOVED)
    }
}

/** Keeps a StateFlow hot for the duration of the test. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
