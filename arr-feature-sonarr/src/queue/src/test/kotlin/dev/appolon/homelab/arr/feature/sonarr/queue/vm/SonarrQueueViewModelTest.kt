package dev.appolon.homelab.arr.feature.sonarr.queue.vm

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.QueueResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueStatus
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.sonarr.queue.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.queue.data.QueueRemoveOptions
import dev.appolon.homelab.arr.feature.sonarr.queue.data.SonarrQueueRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
class SonarrQueueViewModelTest {

    private val serverId = ServerId("server-1")
    private val otherServer = ServerId("server-2")
    private lateinit var fake: FakeSonarrQueue
    private lateinit var store: FakeEntityStore
    private val signals = MutableSharedFlow<ServerId>(extraBufferCapacity = 8)

    private fun viewModel(
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
    ): SonarrQueueViewModel = SonarrQueueViewModel(
        serverId = serverId,
        repository = SonarrQueueRepository(
            serverId = serverId,
            store = store,
            queueDetailsApi = fake.queueDetails,
            queueApi = fake.queue,
            queueActionApi = fake.queueAction,
            queueStatusApi = fake.queueStatus,
            commandRunner = { _, _, _, _ -> dev.appolon.homelab.arr.core.ArrResult.ok(Unit) },
            actionQueue = ActionQueueImpl(store),
            ioContext = StandardTestDispatcher(scheduler),
        ),
        refreshSignals = signals,
    )

    private fun queueItem(id: Int) = QueueResource(
        id = id,
        seriesId = id,
        episodeId = id + 100,
        seasonNumber = 1,
        series = SeriesResource(id = id, title = "Show $id"),
        episode = EpisodeResource(id = id + 100, seasonNumber = 1, episodeNumber = id),
        title = "Release.$id",
        status = QueueStatus.DOWNLOADING,
        downloadId = "dl-$id",
        outputPath = "/downloads/$id",
        size = 10.0,
        sizeleft = 2.0,
    )

    @Before
    fun setUp() {
        fake = FakeSonarrQueue()
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads content through the LCE states`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)
        val vm = viewModel(testScheduler)
        assertEquals(LceState.Loading, vm.lce.value)

        vm.lce.collectWhileActive(this)
        vm.overallProgress.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrQueueContent>).data
        assertEquals(listOf(1), content.items.map { it.id })
        assertEquals("Show 1", content.items.single().title)
        assertEquals("S01E01", content.items.single().episodeLabel)
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
        fake.queueStore[1] = queueItem(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.allItems.size)

        fake.queueStore[2] = queueItem(2)
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
        fake.queueStore[1] = queueItem(1)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "Queue.delete") IOException("offline") else null }

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
        fake.queueStore[1] = queueItem(1)
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
        fake.queueStore[1] = queueItem(1).copy(status = QueueStatus.QUEUED)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "QueueAction.create") IOException("offline") else null }

        vm.grab(vm.state.value.allItems.single())
        advanceUntilIdle()

        assertEquals(QueueNotice.GRAB_QUEUED, vm.state.value.notice)
        assertTrue(vm.state.value.allItems.single().queuedOffline)
    }

    @Test
    fun `filtering narrows the content without flipping to empty`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1).copy(status = QueueStatus.QUEUED)
        fake.queueStore[2] = queueItem(2).copy(status = QueueStatus.FAILED)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setFilter(dev.appolon.homelab.arr.feature.sonarr.queue.logic.SonarrQueueFilter.FAILED)
        runCurrent()

        val content = (vm.lce.value as LceState.Content<SonarrQueueContent>).data
        assertEquals(listOf(2), content.items.map { it.id })
    }

    @Test
    fun `consumeNotice clears the transient notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)
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
