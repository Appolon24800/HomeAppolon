package dev.appolon.homelab.arr.sync.live

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.sync.ArrEvent
import dev.appolon.homelab.arr.sync.LiveConnectionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LiveConnectionManagerTest {
    private val serverA = LiveServerSpec(
        endpoint = ServerEndpoint(ServerId("a"), baseUrl = "http://a/", apiKey = "key-a"),
        appType = AppType.RADARR,
    )
    private val serverB = LiveServerSpec(
        endpoint = ServerEndpoint(ServerId("b"), baseUrl = "http://b/", apiKey = "key-b"),
        appType = AppType.SONARR,
    )

    private class FakeConnection(
        private val spec: LiveServerSpec,
        private val connectImmediately: Boolean = true,
    ) : LiveConnection {
        override val serverId get() = spec.serverId
        override val appType get() = spec.appType
        private val _state = MutableStateFlow(LiveConnectionState.DISCONNECTED)
        override val state: StateFlow<LiveConnectionState> = _state.asStateFlow()
        private val _events = MutableSharedFlow<ArrEvent>(extraBufferCapacity = 64)
        override val events: SharedFlow<ArrEvent> = _events.asSharedFlow()
        var starts = 0
        var stops = 0

        override suspend fun start() {
            starts++
            _state.value = if (connectImmediately) LiveConnectionState.CONNECTED else LiveConnectionState.CONNECTING
        }

        override suspend fun stop() {
            stops++
            _state.value = LiveConnectionState.DISCONNECTED
        }

        fun emit(event: ArrEvent) {
            _events.tryEmit(event)
        }

        fun drop() {
            _state.value = LiveConnectionState.RETRYING
        }

    }

    @Test
    fun `an event storm inside the window collapses to per-entity last write`() = runTest {
        val fake = FakeConnection(serverA)
        val manager = LiveConnectionManager(
            scope = backgroundScope,
            connectivity = flowOf(true),
            specs = { listOf(serverA) },
            connectionFactory = { fake },
            conflateWindowMillis = 250,
            clockMillis = testScheduler::currentTime,
        )
        val out = mutableListOf<ArrEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.events.collect { out += it }
        }
        manager.start()
        runCurrent()
        val id = serverA.serverId
        fake.emit(ArrEvent.EntitiesChanged(id, "movie", "updated"))
        fake.emit(ArrEvent.EntitiesChanged(id, "movie", "deleted"))
        fake.emit(ArrEvent.QueueChanged(id))
        fake.emit(ArrEvent.EntitiesChanged(id, "series", "updated"))
        advanceTimeBy(250)
        runCurrent()
        assertEquals(
            listOf(
                ArrEvent.EntitiesChanged(id, "movie", "deleted"),
                ArrEvent.QueueChanged(id),
                ArrEvent.EntitiesChanged(id, "series", "updated"),
            ),
            out,
        )
        collector.cancel()
    }

    @Test
    fun `bursts separated by more than the window both flush in full`() = runTest {
        val fake = FakeConnection(serverA)
        val manager = LiveConnectionManager(
            scope = backgroundScope,
            connectivity = flowOf(true),
            specs = { listOf(serverA) },
            connectionFactory = { fake },
            conflateWindowMillis = 250,
            clockMillis = testScheduler::currentTime,
        )
        val out = mutableListOf<ArrEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.events.collect { out += it }
        }
        manager.start()
        runCurrent()
        val id = serverA.serverId
        fake.emit(ArrEvent.EntitiesChanged(id, "movie", "updated"))
        advanceTimeBy(250)
        runCurrent()
        fake.emit(ArrEvent.EntitiesChanged(id, "movie", "deleted"))
        advanceTimeBy(250)
        runCurrent()
        assertEquals(
            listOf(
                ArrEvent.EntitiesChanged(id, "movie", "updated"),
                ArrEvent.EntitiesChanged(id, "movie", "deleted"),
            ),
            out,
        )
        collector.cancel()
    }

    @Test
    fun `notifications never conflate`() = runTest {
        val fake = FakeConnection(serverA)
        val manager = LiveConnectionManager(
            scope = backgroundScope,
            connectivity = flowOf(true),
            specs = { listOf(serverA) },
            connectionFactory = { fake },
            conflateWindowMillis = 250,
            clockMillis = testScheduler::currentTime,
        )
        val out = mutableListOf<ArrEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.events.collect { out += it }
        }
        manager.start()
        runCurrent()
        val id = serverA.serverId
        fake.emit(ArrEvent.NotificationIssued(id, "grab", "Grabbed", "Movie grabbed"))
        fake.emit(ArrEvent.NotificationIssued(id, "grab", "Grabbed", "Other grabbed"))
        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, out.size)
        collector.cancel()
    }

    @Test
    fun `events flow carries no replay`() = runTest {
        val fake = FakeConnection(serverA)
        val manager = LiveConnectionManager(
            scope = backgroundScope,
            connectivity = flowOf(true),
            specs = { listOf(serverA) },
            connectionFactory = { fake },
            conflateWindowMillis = 250,
            clockMillis = testScheduler::currentTime,
        )
        val out = mutableListOf<ArrEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.events.collect { out += it }
        }
        manager.start()
        runCurrent()
        fake.emit(ArrEvent.QueueChanged(serverA.serverId))
        advanceTimeBy(250)
        runCurrent()
        assertEquals(1, out.size)
        val late = mutableListOf<ArrEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.events.collect { late += it }
        }
        assertTrue("a late subscriber must not see replayed events", late.isEmpty())
    }

    @Test
    fun `refresh starts added servers and stops removed ones`() = runTest {
        var current = listOf(serverA)
        val created = mutableListOf<FakeConnection>()
        val manager = LiveConnectionManager(
            scope = backgroundScope,
            connectivity = flowOf(true),
            specs = { current },
            connectionFactory = { spec -> FakeConnection(spec).also { created += it } },
            clockMillis = testScheduler::currentTime,
        )
        manager.start()
        runCurrent()
        assertEquals(1, created.size)
        assertEquals(1, created[0].starts)
        assertEquals(setOf(ServerId("a")), manager.states.value.keys)

        current = listOf(serverA, serverB)
        manager.refresh()
        runCurrent()
        assertEquals(2, created.size)
        assertEquals(1, created[1].starts)
        assertEquals(setOf(ServerId("a"), ServerId("b")), manager.states.value.keys)

        current = listOf(serverA)
        manager.refresh()
        runCurrent()
        assertEquals(1, created[1].stops)
        assertEquals(0, created[0].stops)
        assertEquals(setOf(ServerId("a")), manager.states.value.keys)
    }

    @Test
    fun `an edited endpoint rebuilds that server's connection`() = runTest {
        var current = listOf(serverA)
        val created = mutableListOf<FakeConnection>()
        val manager = LiveConnectionManager(
            scope = backgroundScope,
            connectivity = flowOf(true),
            specs = { current },
            connectionFactory = { spec -> FakeConnection(spec).also { created += it } },
            clockMillis = testScheduler::currentTime,
        )
        manager.start()
        runCurrent()
        current = listOf(serverA.copy(endpoint = serverA.endpoint.copy(apiKey = "rotated")))
        manager.refresh()
        runCurrent()
        assertEquals(2, created.size)
        assertEquals(1, created[0].stops)
        assertEquals(1, created[1].starts)
    }

    @Test
    fun `returning connectivity restarts only stalled connections`() = runTest {
        val connectivity = MutableStateFlow(true)
        val fake = FakeConnection(serverA)
        val manager = LiveConnectionManager(
            scope = backgroundScope,
            connectivity = connectivity,
            specs = { listOf(serverA) },
            connectionFactory = { fake },
            clockMillis = testScheduler::currentTime,
        )
        manager.start()
        runCurrent()
        assertEquals(1, fake.starts)
        connectivity.value = false
        runCurrent()
        assertEquals("going offline is passive; the backoff loop owns retries", 1, fake.starts)
        fake.drop()
        connectivity.value = true
        runCurrent()
        assertEquals(2, fake.starts)
        assertEquals(1, fake.stops)
    }

    @Test
    fun `initial online emission never restarts a negotiating connection`() = runTest {
        val fake = FakeConnection(serverA, connectImmediately = false)
        val manager = LiveConnectionManager(
            scope = backgroundScope,
            connectivity = flowOf(true),
            specs = { listOf(serverA) },
            connectionFactory = { fake },
            clockMillis = testScheduler::currentTime,
        )
        manager.start()
        runCurrent()
        assertEquals(1, fake.starts)
        assertEquals(0, fake.stops)
    }

    @Test
    fun `stop tears every connection down`() = runTest {
        val fake = FakeConnection(serverA)
        val manager = LiveConnectionManager(
            scope = backgroundScope,
            connectivity = flowOf(true),
            specs = { listOf(serverA) },
            connectionFactory = { fake },
            clockMillis = testScheduler::currentTime,
        )
        manager.start()
        runCurrent()
        manager.stop()
        runCurrent()
        assertEquals(1, fake.stops)
        assertTrue(manager.states.value.isEmpty())
    }
}
