package dev.appolon.homelab.arr.sync.live

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.sync.ArrEvent
import dev.appolon.homelab.arr.sync.LiveConnectionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val bazarrSpec = LiveServerSpec(
    endpoint = ServerEndpoint(
        serverId = ServerId("bazarr-1"),
        baseUrl = "http://host:6767/",
        apiKey = "key",
    ),
    appType = AppType.BAZARR,
)

private class RecordingSocket : TextSocket {
    val sent = mutableListOf<String>()
    var closeCount = 0
        private set

    override fun send(text: String): Boolean {
        sent += text
        return true
    }

    override fun close(code: Int, reason: String): Boolean {
        closeCount++
        return true
    }
}

private class FakeSocketFactory : TextSocketFactory {
    val listeners = mutableListOf<TextSocketListener>()
    val sockets = mutableListOf<RecordingSocket>()
    val connectCount get() = sockets.size

    override fun connect(listener: TextSocketListener): TextSocket {
        listeners += listener
        val socket = RecordingSocket()
        sockets += socket
        listener.onOpen()
        return socket
    }

    fun deliver(text: String) = listeners.last().onMessage(text)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BazarrLiveConnectionTest {
    @Test
    fun `completes the engineio handshake and answers server pings`() = runTest {
        val factory = FakeSocketFactory()
        val connection = BazarrLiveConnection(bazarrSpec, backgroundScope, socketFactory = factory)
        connection.start()
        runCurrent()
        assertEquals(LiveConnectionState.CONNECTING, connection.state.value)
        factory.deliver("""0{"sid":"abc","pingInterval":25000,"pingTimeout":20000}""")
        runCurrent()
        assertEquals(listOf("40"), factory.sockets.single().sent)
        factory.deliver("""40{"sid":"ns"}""")
        runCurrent()
        assertEquals(LiveConnectionState.CONNECTED, connection.state.value)
        factory.deliver("2")
        runCurrent()
        assertEquals(listOf("40", "3"), factory.sockets.single().sent)
    }

    @Test
    fun `maps socketio events to arr events and drops chatter`() = runTest {
        val factory = FakeSocketFactory()
        val connection = BazarrLiveConnection(bazarrSpec, backgroundScope, socketFactory = factory)
        val received = mutableListOf<ArrEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            connection.events.collect { received += it }
        }
        connection.start()
        runCurrent()
        completeHandshake(factory)
        factory.deliver("""42["movie",{"id":1,"action":"updated"}]""")
        factory.deliver("""42["series",{}]""")
        factory.deliver("""42["badges",{"episodes":3,"movies":0}]""")
        runCurrent()
        assertEquals(
            listOf(
                ArrEvent.EntitiesChanged(bazarrSpec.serverId, "movie", "updated"),
                ArrEvent.EntitiesChanged(bazarrSpec.serverId, "series", null),
            ),
            received,
        )
        collector.cancel()
    }

    @Test
    fun `falls back to polling when the socketio handshake never completes`() = runTest {
        val factory = FakeSocketFactory()
        val connection = BazarrLiveConnection(
            bazarrSpec,
            backgroundScope,
            backoff = ExponentialBackoff(baseMillis = 10, capMillis = 100, jitterRatio = 0.0),
            socketFactory = factory,
            pollPeriodMillis = 60_000,
            handshakeTimeoutMillis = 100,
        )
        val received = mutableListOf<ArrEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            connection.events.collect { received += it }
        }
        connection.start()
        // Two handshake attempts time out: 100ms watchdog + 10ms backoff + 100ms watchdog.
        advanceTimeBy(250)
        runCurrent()
        assertEquals(
            listOf(
                ArrEvent.EntitiesChanged(bazarrSpec.serverId, "movie", null),
                ArrEvent.EntitiesChanged(bazarrSpec.serverId, "series", null),
            ),
            received,
        )
        assertEquals(LiveConnectionState.CONNECTED, connection.state.value)
        assertEquals("no further sockets once polling", 2, factory.connectCount)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(4, received.size)
        collector.cancel()
    }

    @Test
    fun `reconnects with backoff after the namespace disconnects`() = runTest {
        val factory = FakeSocketFactory()
        val connection = BazarrLiveConnection(
            bazarrSpec,
            backgroundScope,
            backoff = ExponentialBackoff(baseMillis = 50, capMillis = 1_000, jitterRatio = 0.0),
            socketFactory = factory,
        )
        connection.start()
        runCurrent()
        completeHandshake(factory)
        factory.deliver("41")
        runCurrent()
        assertEquals(LiveConnectionState.RETRYING, connection.state.value)
        advanceTimeBy(50)
        runCurrent()
        assertEquals(2, factory.connectCount)
        completeHandshake(factory)
        assertEquals(LiveConnectionState.CONNECTED, connection.state.value)
    }

    @Test
    fun `stop closes the socket and settles disconnected`() = runTest {
        val factory = FakeSocketFactory()
        val connection = BazarrLiveConnection(bazarrSpec, backgroundScope, socketFactory = factory)
        connection.start()
        runCurrent()
        completeHandshake(factory)
        connection.stop()
        runCurrent()
        assertEquals(LiveConnectionState.DISCONNECTED, connection.state.value)
        assertTrue(factory.sockets.single().closeCount >= 1)
    }

    /** Drives a socket through engine.io OPEN and the socket.io connect ack. */
    private fun completeHandshake(factory: FakeSocketFactory) {
        factory.deliver("""0{"sid":"abc","pingInterval":25000,"pingTimeout":20000}""")
        factory.deliver("""40{"sid":"ns"}""")
    }
}
