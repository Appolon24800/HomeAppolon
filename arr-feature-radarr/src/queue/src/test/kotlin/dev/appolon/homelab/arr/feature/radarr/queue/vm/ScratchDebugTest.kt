package dev.appolon.homelab.arr.feature.radarr.queue.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QueueResource
import dev.appolon.homelab.arr.api.radarr.model.QueueStatus
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.radarr.queue.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.queue.data.RadarrQueueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScratchDebugTest {

    @Test
    fun trace() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fake = FakeRadarrQueue()
        fake.queueItems += QueueResource(
            id = 1,
            movieId = 1,
            movie = MovieResource(id = 1, title = "Movie 1"),
            title = "Release.1",
            status = QueueStatus.DOWNLOADING,
            size = 10.0,
            sizeleft = 2.0,
        )
        val store = FakeEntityStore()
        FakeEntityStore.DEBUG = true
        val signals = MutableSharedFlow<ServerId>(extraBufferCapacity = 8)
        val repo = RadarrQueueRepository(
            serverId = ServerId("s"),
            store = store,
            queueDetailsApi = fake,
            queueApi = fake,
            queueActionApi = fake,
            queueStatusApi = fake,
            commandRunner = { _, _, _, _ -> dev.appolon.homelab.arr.core.ArrResult.ok(Unit) },
            actionQueue = ActionQueueImpl(store),
            ioContext = StandardTestDispatcher(testScheduler),
        )
        val vm = RadarrQueueViewModel(
            serverId = ServerId("s"),
            repository = repo,
            refreshSignals = signals,
        )
        advanceUntilIdle()
        println("DBG no-collectors: items=${vm.state.value.allItems.map { it.id }} loaded=${vm.state.value.loaded}")
    }
}
