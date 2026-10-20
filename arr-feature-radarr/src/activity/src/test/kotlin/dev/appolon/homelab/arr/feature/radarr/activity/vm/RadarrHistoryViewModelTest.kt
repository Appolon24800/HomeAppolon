package dev.appolon.homelab.arr.feature.radarr.activity.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.model.HistoryResource
import dev.appolon.homelab.arr.api.radarr.model.MovieHistoryEventType
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.radarr.activity.FakeActivityEntityStore
import dev.appolon.homelab.arr.feature.radarr.activity.data.ACTIVITY_PAGE_SIZE
import dev.appolon.homelab.arr.feature.radarr.activity.data.RadarrHistoryRepository
import dev.appolon.homelab.arr.feature.radarr.activity.logic.HistoryDateWindow
import dev.appolon.homelab.arr.feature.radarr.activity.logic.HistoryEventTypeFilter
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrHistoryViewModelTest {

    private val serverId = ServerId("server-1")
    private val otherServer = ServerId("server-2")
    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private lateinit var fake: FakeRadarrQueue
    private lateinit var store: FakeActivityEntityStore
    private val signals = MutableSharedFlow<ServerId>(extraBufferCapacity = 8)

    private fun viewModel(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): RadarrHistoryViewModel =
        RadarrHistoryViewModel(
            serverId = serverId,
            repository = RadarrHistoryRepository(
                serverId = serverId,
                store = store,
                historyApi = fake,
                actionQueue = ActionQueueImpl(store),
                ioContext = StandardTestDispatcher(scheduler),
            ),
            refreshSignals = signals,
            now = { now },
        )

    private fun event(
        id: Int,
        eventType: MovieHistoryEventType = MovieHistoryEventType.GRABBED,
        date: Instant = Instant.ofEpochSecond(1_800_000_000L - id * 3_600L),
    ) = HistoryResource(
        id = id,
        movieId = id,
        sourceTitle = "Release.$id",
        date = date.toString(),
        eventType = eventType,
        downloadId = if (eventType == MovieHistoryEventType.DOWNLOAD_FAILED) "dl-$id" else null,
        movie = MovieResource(id = id, title = "Movie $id"),
    )

    @Before
    fun setUp() {
        fake = FakeRadarrQueue()
        store = FakeActivityEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads content through the LCE states newest first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.historyItems += event(1, date = Instant.parse("2026-10-20T11:00:00Z"))
        fake.historyItems += event(2, date = Instant.parse("2026-10-20T10:00:00Z"))
        val vm = viewModel(testScheduler)
        assertEquals(LceState.Loading, vm.lce.value)

        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrHistoryContent>).data
        assertEquals(listOf(1, 2), content.events.map { it.id })
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
    fun `initial load failure with no cache renders the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.errorFor = { op -> if (op == "history") IOException("offline") else null }
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
        assertTrue((vm.lce.value as LceState.Error).error is ArrError.Network)
    }

    @Test
    fun `filters narrow the content without flipping to empty`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.historyItems += event(1, MovieHistoryEventType.GRABBED)
        fake.historyItems += event(2, MovieHistoryEventType.DOWNLOAD_FAILED)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setTypeFilter(HistoryEventTypeFilter.FAILED)
        runCurrent()

        val content = (vm.lce.value as LceState.Content<RadarrHistoryContent>).data
        assertEquals(listOf(2), content.events.map { it.id })
        assertTrue(content.events.single().failedImport)
    }

    @Test
    fun `movie query matches title and release name`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.historyItems += event(1)
        fake.historyItems += event(2)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setMovieQuery("release.2")
        runCurrent()

        val content = (vm.lce.value as LceState.Content<RadarrHistoryContent>).data
        assertEquals(listOf(2), content.events.map { it.id })
    }

    @Test
    fun `date window narrows and loadMore stops once an older page is fetched`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        // Three pages; only page one's rows fall inside the last 24 hours.
        repeat(ACTIVITY_PAGE_SIZE * 3) { index ->
            val at = if (index < ACTIVITY_PAGE_SIZE) {
                Instant.parse("2026-10-20T11:00:00Z")
            } else {
                Instant.parse("2026-10-01T00:00:00Z")
            }
            fake.historyItems += event(index + 1, date = at)
        }
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setDateWindow(HistoryDateWindow.LAST_24_HOURS)
        runCurrent()
        var content = (vm.lce.value as LceState.Content<RadarrHistoryContent>).data
        assertEquals(ACTIVITY_PAGE_SIZE, content.events.size)
        // The oldest loaded row is still inside the window, so more pages may apply.
        assertTrue(content.canLoadMore)

        vm.loadMore()
        advanceUntilIdle()
        content = (vm.lce.value as LceState.Content<RadarrHistoryContent>).data
        // Page two predates the window: filtering stays narrow and loading stops early.
        assertEquals(ACTIVITY_PAGE_SIZE, content.events.size)
        assertFalse(content.canLoadMore)
        assertFalse(vm.state.value.endReached)
    }

    @Test
    fun `loadMore appends the next server page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        repeat(ACTIVITY_PAGE_SIZE + 2) { index ->
            fake.historyItems += event(index + 1)
        }
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        assertEquals(ACTIVITY_PAGE_SIZE, vm.state.value.allEvents.size)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(ACTIVITY_PAGE_SIZE + 2, vm.state.value.allEvents.size)
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `live signals for this server refresh, others do not`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.historyItems += event(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.allEvents.size)

        fake.historyItems += event(2)
        signals.tryEmit(otherServer)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, vm.state.value.allEvents.size)

        signals.tryEmit(serverId)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1, 2), vm.state.value.allEvents.map { it.id })
    }

    @Test
    fun `offline mark failed shows the queued notice and badges the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.historyItems += event(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.errorFor = { op -> if (op == "markHistoryFailed") IOException("offline") else null }

        vm.markFailed(vm.state.value.allEvents.single())
        advanceUntilIdle()

        assertEquals(HistoryNotice.MARK_FAILED_QUEUED, vm.state.value.notice)
        val row = vm.state.value.allEvents.single()
        assertEquals(MovieHistoryEventType.DOWNLOAD_FAILED, row.eventType)
        assertTrue(row.queuedOffline)
        assertEquals(1, vm.state.value.pendingActions)
    }

    @Test
    fun `online mark failed reports the synced notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.historyItems += event(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.markFailed(vm.state.value.allEvents.single())
        advanceUntilIdle()

        assertEquals(HistoryNotice.MARKED_FAILED, vm.state.value.notice)
        assertEquals(0, vm.state.value.pendingActions)
    }

    @Test
    fun `consumeNotice clears the transient notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.historyItems += event(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.markFailed(vm.state.value.allEvents.single())
        advanceUntilIdle()
        vm.consumeNotice()

        assertEquals(null, vm.state.value.notice)
    }
}

/** Keeps a StateFlow hot for the duration of the test. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
