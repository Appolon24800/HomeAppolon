package dev.appolon.homelab.arr.sync.live

import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * One decoded engine.io / socket.io text frame. Only the shapes Bazarr
 * actually emits are modeled; anything else lands in [Unknown] and is ignored.
 */
internal sealed interface EngineIoFrame {
    /** engine.io OPEN: `0{"sid":...,"pingInterval":...,"pingTimeout":...}`. */
    data class Open(val pingIntervalMillis: Long, val pingTimeoutMillis: Long) : EngineIoFrame

    data object Ping : EngineIoFrame

    data object Pong : EngineIoFrame

    data object Close : EngineIoFrame

    data object Noop : EngineIoFrame

    /** socket.io namespace connect ack, `40{...}` from the server. */
    data object SocketIoConnected : EngineIoFrame

    /** socket.io namespace disconnect, `41`. */
    data object SocketIoDisconnected : EngineIoFrame

    /** socket.io connect error, `44{...}`. */
    data class SocketIoConnectError(val reason: String) : EngineIoFrame

    /** socket.io event, `42["name", payload?]`. */
    data class SocketIoEvent(val name: String, val payloadJson: String?) : EngineIoFrame

    data class Unknown(val raw: String) : EngineIoFrame
}

/**
 * Minimal engine.io v4 / socket.io v5 text-protocol parser, just enough for
 * Bazarr's event stream. Control packets are plain prefixes; event payloads
 * are JSON arrays parsed with the shared project [ArrJson].
 */
internal object EngineIo {
    /** engine.io v4 batches packets into one frame separated by \\u001e. */
    private const val PACKET_SEPARATOR = '\u001e'

    /** Client -> server namespace connect for the root namespace. */
    const val SOCKET_IO_CONNECT = "40"

    /** Client -> server engine.io pong. */
    const val ENGINE_IO_PONG = "3"

    fun parseAll(text: String): List<EngineIoFrame> =
        text.split(PACKET_SEPARATOR).map(::parse)

    fun parse(frame: String): EngineIoFrame = when (frame.firstOrNull()) {
        '0' -> parseOpen(frame)
        '1' -> EngineIoFrame.Close
        '2' -> EngineIoFrame.Ping
        '3' -> EngineIoFrame.Pong
        '4' -> parseSocketIo(frame)
        '6' -> EngineIoFrame.Noop
        else -> EngineIoFrame.Unknown(frame)
    }

    private fun parseOpen(frame: String): EngineIoFrame {
        val payload = runCatching { ArrJson.parseToJsonElement(frame.substring(1)).jsonObject }
            .getOrNull()
            ?: return EngineIoFrame.Unknown(frame)
        return EngineIoFrame.Open(
            pingIntervalMillis = payload.longOf("pingInterval") ?: DEFAULT_PING_INTERVAL_MILLIS,
            pingTimeoutMillis = payload.longOf("pingTimeout") ?: DEFAULT_PING_TIMEOUT_MILLIS,
        )
    }

    private fun parseSocketIo(frame: String): EngineIoFrame {
        val sub = frame.substring(1)
        return when (sub.firstOrNull()) {
            '0' -> EngineIoFrame.SocketIoConnected
            '1' -> EngineIoFrame.SocketIoDisconnected
            '4' -> EngineIoFrame.SocketIoConnectError(sub.substring(1))
            '2' -> parseEvent(frame, sub.substring(1))
            else -> EngineIoFrame.Unknown(frame)
        }
    }

    private fun parseEvent(frame: String, json: String): EngineIoFrame {
        val array = runCatching { ArrJson.parseToJsonElement(json) as? JsonArray }
            .getOrNull()
            ?: return EngineIoFrame.Unknown(frame)
        val name = (array.getOrNull(0) as? JsonPrimitive)
            ?.takeUnless { it is JsonNull }
            ?.content
            ?: return EngineIoFrame.Unknown(frame)
        val payload = array.getOrNull(1)
            ?.takeUnless { it is JsonNull }
            ?.toString()
        return EngineIoFrame.SocketIoEvent(name = name, payloadJson = payload)
    }

    private fun JsonObject.longOf(key: String): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content.toLongOrNull()
    }

    private const val DEFAULT_PING_INTERVAL_MILLIS = 25_000L
    private const val DEFAULT_PING_TIMEOUT_MILLIS = 20_000L
}

/**
 * Bazarr mounts socket.io at its conventional `/socket.io/` path under any
 * base path prefix. `EIO=4` pins engine.io v4 and `transport=websocket` skips
 * the HTTP polling fallback entirely.
 */
internal fun bazarrSocketUrl(baseUrl: String): String =
    baseUrl.trimEnd('/') + "/socket.io/?EIO=4&transport=websocket"
