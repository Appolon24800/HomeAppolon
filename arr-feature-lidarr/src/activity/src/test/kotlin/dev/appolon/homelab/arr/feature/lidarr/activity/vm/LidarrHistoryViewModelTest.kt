package dev.appolon.homelab.arr.feature.lidarr.activity.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.lidarr.activity.FakeActivityEntityStore
import dev.appolon.homelab.arr.feature.lidarr.activity.data.LIDARR_ACTIVITY_PAGE_SIZE
import dev.appolon.homelab.arr.feature.lidarr.activity.data.LidarrHistoryRepository
import dev.appolon.homelab.arr.feature.lidarr.activity.logic.LidarrHistoryDateWindow
import dev.appolon.homelab.arr.feature.lidarr.activity.logic.LidarrHistoryFilter
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
class LidarrHistoryViewModelTest {

    private val serverId = ServerId("server-1")
    private val otherServer = ServerId("server-2")
    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private lateinit var fake: FakeLidarrQueue
    private lateinit var store: FakeActivityEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private val signals = MutableSharedFlow<ServerId>(extraBufferCapacity = 8)

    private fun viewModel(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): LidarrHistoryViewModel =
        LidarrHistoryViewModel(
            serverId = serverId,
            repository = LidarrHistoryRepository(
                serverId = serverId,
                store = store,
                historyApi = fake,
                actionQueue = actionQueue,
                ioContext = StandardTestDispatcher(scheduler),
            ),
            refreshSignals = signals,
            now = { now },
        )

    private fun event(
        id: Int,
        eventType: EntityHistoryEventType = EntityHistoryEventType.GRABBED,
        date: Instant = Instant.ofEpochSecond(1_800_000_000L - id * 3_600L),
    ) = HistoryResource(
        id = id,
        albumId = id,
        artistId = id,
        trackId = id,
        sourceTitle = "Release.$id",
        date = date.toString(),
        eventType = eventType,
        downloadId = if (eventType == EntityHistoryEventType.DOWNLOAD_FAILED) "dl-$id" else null,
    )

    @Before
    fun setUp() {
        fake = FakeLidarrQueue()
        store = FakeActivityEntityStore()
        actionQueue = ActionQueueImpl(store)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads content through the LCE states newest first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1, date = Instant.parse("2026-10-20T11:00:00Z"))
        fake.history[2] = event(2, date = Instant.parse("2026-10-20T10:00:00Z"))
        val vm = viewModel(testScheduler)
        assertEquals(LceState.Loading, vm.lce.value)

        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrHistoryContent>).data
        assertEquals(listOf(1, 2), content.events.map { it.id })
        assertEquals("Release.1", content.events.first().sourceTitle)
        assertTrue(content.events.first().canMarkFailed)
    }

    @Test
    fun `empty feed renders the empty state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertEquals(LceState.Empty, vm.lce.value)
    }

    @Test
    fun `first fetch failure renders the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.error = { if (it == "getHistory") IOException("offline") else null }
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
    }

    @Test
    fun `loadMore appends the next page while records remain`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        repeat(LIDARR_ACTIVITY_PAGE_SIZE * 2 + 3) { index -> fake.history[index + 1] = event(index + 1) }
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        assertEquals(LIDARR_ACTIVITY_PAGE_SIZE, vm.state.value.allEvents.size)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(LIDARR_ACTIVITY_PAGE_SIZE * 2, vm.state.value.allEvents.size)

        vm.loadMore()
        advanceUntilIdle()
        // Third page carries the last three rows and the cursor reports the end.
        assertEquals(LIDARR_ACTIVITY_PAGE_SIZE * 2 + 3, vm.state.value.allEvents.size)
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `type filters narrow the visible events without refetching`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1, EntityHistoryEventType.GRABBED)
        fake.history[2] = event(2, EntityHistoryEventType.TRACK_FILE_RETAGGED)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setFilter(LidarrHistoryFilter.RETAGGED)
        runCurrent()

        val content = (vm.lce.value as LceState.Content<LidarrHistoryContent>).data
        assertEquals(listOf(2), content.events.map { it.id })
    }

    @Test
    fun `date windows filter against the injected clock`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1, date = Instant.parse("2026-10-20T11:00:00Z"))
        fake.history[2] = event(2, date = Instant.parse("2026-10-01T00:00:00Z"))
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setDateWindow(LidarrHistoryDateWindow.LAST_24_HOURS)
        runCurrent()

        val content = (vm.lce.value as LceState.Content<LidarrHistoryContent>).data
        assertEquals(listOf(1), content.events.map { it.id })
    }

    @Test
    fun `query matches the release name`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1)
        fake.history[2] = event(2)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setQuery("release.1")
        runCurrent()

        val content = (vm.lce.value as LceState.Content<LidarrHistoryContent>).data
        assertEquals(listOf(1), content.events.map { it.id })
    }

    @Test
    fun `markFailed online updates the row and posts the notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.markFailed(vm.state.value.allEvents.single())
        advanceUntilIdle()

        assertEquals(LidarrHistoryNotice.MARKED_FAILED, vm.state.value.notice)
        assertEquals(EntityHistoryEventType.DOWNLOAD_FAILED, vm.state.value.allEvents.single().eventType)
        assertTrue(1 in fake.failedHistoryIds)

        vm.consumeNotice()
        assertEquals(null, vm.state.value.notice)
    }

    @Test
    fun `markFailed offline queues the write and badges the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { if (it == "failHistory") IOException("offline") else null }

        vm.markFailed(vm.state.value.allEvents.single())
        advanceUntilIdle()

        assertEquals(LidarrHistoryNotice.MARK_FAIL_QUEUED, vm.state.value.notice)
        assertTrue(vm.state.value.allEvents.single().queuedOffline)
        assertEquals(1, actionQueue.pending().size)
        assertEquals(1, vm.state.value.pendingActions)
    }

    @Test
    fun `failed imports expose the retry affordance`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1, EntityHistoryEventType.DOWNLOAD_FAILED)
        fake.history[2] = event(
            2,
            EntityHistoryEventType.ALBUM_IMPORT_INCOMPLETE,
            date = Instant.parse("2026-10-19T00:00:00Z"),
        ).copy(downloadId = "dl-2")
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val events = vm.state.value.allEvents
        assertTrue(events.first { it.id == 1 }.failedImport)
        assertTrue(events.first { it.id == 2 }.failedImport)
        assertEquals("dl-1", events.first { it.id == 1 }.downloadId)
    }

    @Test
    fun `refresh signals for this server trigger a debounced refresh`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.history[2] = event(2, date = Instant.parse("2026-10-20T11:30:00Z"))

        signals.tryEmit(serverId)
        advanceTimeBy(1_000)
        runCurrent()

        val content = (vm.lce.value as LceState.Content<LidarrHistoryContent>).data
        assertEquals(setOf(1, 2), content.events.map { it.id }.toSet())
    }

    @Test
    fun `refresh signals for other servers are ignored`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.history[1] = event(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.history[2] = event(2, date = Instant.parse("2026-10-20T11:30:00Z"))

        signals.tryEmit(otherServer)
        advanceTimeBy(1_000)
        runCurrent()

        val content = (vm.lce.value as LceState.Content<LidarrHistoryContent>).data
        assertEquals(listOf(1), content.events.map { it.id })
    }
}

/** Keeps a StateFlow hot for the duration of the test. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
