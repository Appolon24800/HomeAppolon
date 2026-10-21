package dev.appolon.homelab.arr.sync.live

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.sync.ArrEvent
import dev.appolon.homelab.arr.sync.LiveConnectionState
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private val radarrSpec = LiveServerSpec(
    endpoint = ServerEndpoint(
        serverId = ServerId("radarr-1"),
        baseUrl = "http://host:7878/radarr/",
        apiKey = "key",
    ),
    appType = AppType.RADARR,
)

/** Scriptable stand-in for the gson-wired Microsoft hub. */
private class FakeHubPort : HubPort {
    var messageHandler: ((String?, String?) -> Unit)? = null
        private set
    var closedHandler: ((Throwable?) -> Unit)? = null
        private set
    var startBehavior: suspend () -> Unit = {}
    var starts = 0
        private set

    override fun onMessage(handler: (String?, String?) -> Unit) {
        messageHandler = handler
    }

    override fun onClosed(handler: (Throwable?) -> Unit) {
        closedHandler = handler
    }

    override suspend fun start() {
        starts++
        startBehavior()
    }

    override suspend fun stop() = Unit
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SignalRLiveConnectionTest {
    @Test
    fun `maps receiveMessage names onto arr events and drops chatter`() = runTest {
        val hub = FakeHubPort()
        val connection = SignalRLiveConnection(radarrSpec, backgroundScope, hubPortFactory = { hub })
        val received = mutableListOf<ArrEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            connection.events.collect { received += it }
        }
        connection.start()
        runCurrent()
        hub.messageHandler?.invoke("movie", "updated")
        hub.messageHandler?.invoke("queue", null)
        hub.messageHandler?.invoke("calendar", null)
        hub.messageHandler?.invoke("health", null)
        hub.messageHandler?.invoke("diskspace", null)
        hub.messageHandler?.invoke("command", null)
        runCurrent()
        assertEquals(
            listOf(
                ArrEvent.EntitiesChanged(radarrSpec.serverId, "movie", "updated"),
                ArrEvent.QueueChanged(radarrSpec.serverId),
                ArrEvent.CalendarChanged(radarrSpec.serverId),
                ArrEvent.HealthChanged(radarrSpec.serverId),
                ArrEvent.DiskSpaceChanged(radarrSpec.serverId),
            ),
            received,
        )
        collector.cancel()
    }

    @Test
    fun `hub url keeps the path prefix and url-encodes the access token`() {
        assertEquals(
            "http://host:7878/radarr/signalr/messages?access_token=abc%2F123",
            signalRHubUrl("http://host:7878/radarr/", "abc/123"),
        )
    }

    @Test
    fun `reconnects with the configured backoff after the hub drops`() = runTest {
        val hub = FakeHubPort()
        val connection = SignalRLiveConnection(
            radarrSpec,
            backgroundScope,
            backoff = ExponentialBackoff(baseMillis = 1_000, capMillis = 30_000, jitterRatio = 0.0),
            hubPortFactory = { hub },
        )
        connection.start()
        runCurrent()
        assertEquals(LiveConnectionState.CONNECTED, connection.state.value)
        hub.closedHandler?.invoke(null)
        runCurrent()
        assertEquals(LiveConnectionState.RETRYING, connection.state.value)
        advanceTimeBy(999)
        runCurrent()
        assertEquals("still waiting out the backoff", 1, hub.starts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, hub.starts)
        assertEquals(LiveConnectionState.CONNECTED, connection.state.value)
    }

    @Test
    fun `a refused start retries instead of surfacing`() = runTest {
        val hub = FakeHubPort()
        var failures = 0
        hub.startBehavior = {
            failures++
            if (failures == 1) throw IOException("connection refused")
        }
        val connection = SignalRLiveConnection(
            radarrSpec,
            backgroundScope,
            backoff = ExponentialBackoff(baseMillis = 100, capMillis = 1_000, jitterRatio = 0.0),
            hubPortFactory = { hub },
        )
        connection.start()
        runCurrent()
        assertEquals(LiveConnectionState.RETRYING, connection.state.value)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, hub.starts)
        assertEquals(LiveConnectionState.CONNECTED, connection.state.value)
    }

    @Test
    fun `stop disconnects for good and a late hub close cannot restart the loop`() = runTest {
        val hub = FakeHubPort()
        val connection = SignalRLiveConnection(
            radarrSpec,
            backgroundScope,
            backoff = ExponentialBackoff(baseMillis = 1, capMillis = 1, jitterRatio = 0.0),
            hubPortFactory = { hub },
        )
        connection.start()
        runCurrent()
        connection.stop()
        runCurrent()
        assertEquals(LiveConnectionState.DISCONNECTED, connection.state.value)
        val startsAtStop = hub.starts
        hub.closedHandler?.invoke(IOException("late close"))
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(startsAtStop, hub.starts)
    }
}
