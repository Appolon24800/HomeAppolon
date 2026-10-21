package dev.appolon.homelab.arr.sync.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineIoTest {
    @Test
    fun `parses engineio control packets`() {
        assertEquals(EngineIoFrame.Ping, EngineIo.parse("2"))
        assertEquals(EngineIoFrame.Pong, EngineIo.parse("3"))
        assertEquals(EngineIoFrame.Close, EngineIo.parse("1"))
        assertEquals(EngineIoFrame.Noop, EngineIo.parse("6"))
    }

    @Test
    fun `parses open with the server ping cadence`() {
        val frame = EngineIo.parse("""0{"sid":"x","upgrades":[],"pingInterval":25000,"pingTimeout":20000}""")
        assertEquals(EngineIoFrame.Open(pingIntervalMillis = 25_000, pingTimeoutMillis = 20_000), frame)
    }

    @Test
    fun `open without cadence fields falls back to engineio defaults`() {
        assertEquals(EngineIoFrame.Open(25_000, 20_000), EngineIo.parse("0{}"))
        assertTrue(EngineIo.parse("0not-an-object") is EngineIoFrame.Unknown)
    }

    @Test
    fun `parses socketio connect acks, disconnects, and errors`() {
        assertEquals(EngineIoFrame.SocketIoConnected, EngineIo.parse("""40{"sid":"y"}"""))
        assertEquals(EngineIoFrame.SocketIoDisconnected, EngineIo.parse("41"))
        assertEquals(
            EngineIoFrame.SocketIoConnectError("""{"message":"unauthorized"}"""),
            EngineIo.parse("""44{"message":"unauthorized"}"""),
        )
    }

    @Test
    fun `parses socketio events with and without a payload`() {
        assertEquals(
            EngineIoFrame.SocketIoEvent("movie", """{"id":1,"action":"updated"}"""),
            EngineIo.parse("""42["movie",{"id":1,"action":"updated"}]"""),
        )
        assertEquals(EngineIoFrame.SocketIoEvent("badges", null), EngineIo.parse("42[\"badges\"]"))
    }

    @Test
    fun `splits batched engineio frames on the record separator`() {
        val frames = EngineIo.parseAll("2\u001e42[\"movie\",{\"action\":\"updated\"}]")
        assertEquals(
            listOf(EngineIoFrame.Ping, EngineIoFrame.SocketIoEvent("movie", """{"action":"updated"}""")),
            frames,
        )
    }

    @Test
    fun `bazarr socket url keeps the path prefix and pins engineio v4 websocket`() {
        assertEquals(
            "http://host:6767/bazarr/socket.io/?EIO=4&transport=websocket",
            bazarrSocketUrl("http://host:6767/bazarr/"),
        )
    }
}
