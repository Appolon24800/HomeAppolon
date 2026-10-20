package dev.appolon.homelab.arr.feature.lidarr.queue.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.lidarr.queue.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.queue.data.QueueRemoveOptions
import dev.appolon.homelab.arr.feature.lidarr.queue.data.LidarrQueueRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
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
class LidarrQueueViewModelTest {

    private val serverId = ServerId("server-1")
    private val otherServer = ServerId("server-2")
    private lateinit var fake: FakeLidarrQueue
    private lateinit var store: FakeEntityStore
    private val signals = MutableSharedFlow<ServerId>(extraBufferCapacity = 8)

    private fun viewModel(
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
    ): LidarrQueueViewModel = LidarrQueueViewModel(
        serverId = serverId,
        repository = LidarrQueueRepository(
            serverId = serverId,
            store = store,
            queueDetailsApi = fake,
            queueApi = fake,
            queueActionApi = fake,
            queueStatusApi = fake,
            commandRunner = { _, _, _, _ -> ArrResult.ok(Unit) },
            actionQueue = ActionQueueImpl(store),
            ioContext = StandardTestDispatcher(scheduler),
        ),
        refreshSignals = signals,
    )

    private fun queueItem(id: Int) = QueueResource(
        id = id,
        artistId = id,
        albumId = id + 50,
        artist = ArtistResource(id = id, artistName = "Artist $id"),
        album = AlbumResource(id = id + 50, title = "Album $id"),
        title = "Release.$id",
        status = "downloading",
        downloadId = "dl-$id",
        outputPath = "/downloads/$id",
        size = 10.0,
        sizeleft = 2.0,
        trackFileCount = 12,
        trackHasFileCount = 5,
    )

    @Before
    fun setUp() {
        fake = FakeLidarrQueue()
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads content through the LCE states`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        val vm = viewModel(testScheduler)
        assertEquals(LceState.Loading, vm.lce.value)

        vm.lce.collectWhileActive(this)
        vm.overallProgress.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrQueueContent>).data
        assertEquals(listOf(1), content.items.map { it.id })
        assertEquals("Artist 1", content.items.single().title)
        assertEquals("Album 1", content.items.single().albumTitle)
        assertEquals(12, content.items.single().trackTotal)
        assertEquals(1, content.overview!!.totalCount)
        assertEquals(0.8f, vm.overallProgress.value)
    }

    @Test
    fun `empty queue renders the empty state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertEquals(LceState.Empty, vm.lce.value)
    }

    @Test
    fun `initial load failure with no cache renders the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.error = { IOException("offline") }
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
        assertTrue((vm.lce.value as LceState.Error).error is ArrError.Network)
    }

    @Test
    fun `live signals for this server refresh, others do not`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.allItems.size)

        fake.queue[2] = queueItem(2)
        signals.tryEmit(otherServer)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, vm.state.value.allItems.size)

        signals.tryEmit(serverId)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1, 2), vm.state.value.allItems.map { it.id })
    }

    @Test
    fun `offline remove shows the queued notice, optimistic removal, and pending count`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "deleteQueue") IOException("offline") else null }

        vm.removeItem(
            vm.state.value.allItems.single(),
            QueueRemoveOptions(removeFromClient = true),
        )
        advanceUntilIdle()

        assertEquals(QueueNotice.REMOVE_QUEUED, vm.state.value.notice)
        assertTrue(vm.state.value.allItems.isEmpty())
        assertEquals(1, vm.state.value.pendingActions)
    }

    @Test
    fun `online retry import reports the synced notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.retryImport(vm.state.value.allItems.single())
        advanceUntilIdle()

        assertEquals(QueueNotice.RETRY_SENT, vm.state.value.notice)
    }

    @Test
    fun `offline grab marks the row as queued offline`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1).copy(status = "queued")
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "grabQueue") IOException("offline") else null }

        vm.grab(vm.state.value.allItems.single())
        advanceUntilIdle()

        assertEquals(QueueNotice.GRAB_QUEUED, vm.state.value.notice)
        assertTrue(vm.state.value.allItems.single().queuedOffline)
    }

    @Test
    fun `filtering narrows the content without flipping to empty`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1).copy(status = "queued")
        fake.queue[2] = queueItem(2).copy(status = "failed")
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setFilter(dev.appolon.homelab.arr.feature.lidarr.queue.logic.LidarrQueueFilter.FAILED)
        runCurrent()

        val content = (vm.lce.value as LceState.Content<LidarrQueueContent>).data
        assertEquals(listOf(2), content.items.map { it.id })
    }

    @Test
    fun `consumeNotice clears the transient notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.retryImport(vm.state.value.allItems.single())
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
