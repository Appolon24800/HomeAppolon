package dev.appolon.homelab.arr.sync.live

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.sync.ArrEvent
import dev.appolon.homelab.arr.sync.LiveConnectionState
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * A text-only websocket: the slice of [okhttp3.WebSocket] this connection
 * needs, public so tests can fake the socket behind [BazarrLiveConnection].
 */
interface TextSocket {
    /** Returns false when the socket is already closed or over its buffer. */
    fun send(text: String): Boolean

    fun close(code: Int, reason: String): Boolean
}

interface TextSocketListener {
    fun onOpen()

    fun onMessage(text: String)

    fun onFailure(error: Throwable)

    fun onClosed(code: Int, reason: String)
}

fun interface TextSocketFactory {
    fun connect(listener: TextSocketListener): TextSocket
}

/**
 * Production [TextSocketFactory] over OkHttp. Built from the per-server
 * client so the API key header, basic auth, proxy, and TLS policy apply to
 * the websocket upgrade exactly as they do to REST calls.
 */
internal class OkHttpTextSocketFactory(
    private val url: String,
    httpClient: OkHttpClient,
) : TextSocketFactory {
    // Zero read timeout: an idle socket is kept alive by engine.io pings, not
    // HTTP timeouts. OkHttp's own pings detect a dead peer without them.
    private val client = httpClient.newBuilder()
        .pingInterval(PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun connect(listener: TextSocketListener): TextSocket {
        val webSocket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()

                override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                    listener.onFailure(t)

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                    listener.onClosed(code, reason)
            },
        )
        return object : TextSocket {
            override fun send(text: String): Boolean = webSocket.send(text)

            override fun close(code: Int, reason: String): Boolean = webSocket.close(code, reason)
        }
    }

    private companion object {
        const val PING_INTERVAL_MILLIS = 15_000L
    }
}

/**
 * Live connection for Bazarr, which speaks engine.io v4 / socket.io v5
 * instead of SignalR. The handshake is: answer engine.io OPEN with the
 * socket.io root-namespace connect (`40`), reply to every server PING (`2`)
 * with a PONG (`3`), then consume `42["event", payload]` frames and map them
 * through [ServarrEventMapper].
 *
 * If the socket.io handshake never completes [MAX_HANDSHAKE_FAILURES] times
 * in a row (wrong server version, proxy stripping upgrades), the connection
 * degrades to a poll emitter that blanket-invalidates Bazarr's entity types
 * every [pollPeriodMillis] so repositories still refresh; state reports
 * CONNECTED there because events are flowing. The next [start] retries the
 * socket.
 */
class BazarrLiveConnection(
    private val spec: LiveServerSpec,
    private val scope: CoroutineScope,
    httpClient: OkHttpClient = OkHttpClient(),
    private val backoff: ExponentialBackoff = ExponentialBackoff(),
    socketFactory: (TextSocketFactory)? = null,
    private val pollPeriodMillis: Long = DEFAULT_POLL_PERIOD_MILLIS,
    private val handshakeTimeoutMillis: Long = DEFAULT_HANDSHAKE_TIMEOUT_MILLIS,
) : LiveConnection {
    private val socketFactory: TextSocketFactory =
        socketFactory ?: OkHttpTextSocketFactory(bazarrSocketUrl(spec.endpoint.baseUrl), httpClient)

    override val serverId: ServerId get() = spec.serverId
    override val appType: AppType get() = spec.appType

    private val _state = MutableStateFlow(LiveConnectionState.DISCONNECTED)
    override val state: StateFlow<LiveConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ArrEvent>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<ArrEvent> = _events.asSharedFlow()

    private val running = AtomicBoolean(false)
    private var loopJob: Job? = null

    @Volatile
    private var socket: TextSocket? = null

    override suspend fun start() {
        if (!running.compareAndSet(false, true)) return
        loopJob = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        var attempt = 0
        var handshakeFailures = 0
        try {
            while (currentCoroutineContext().isActive && running.get()) {
                _state.value = if (attempt == 0) {
                    LiveConnectionState.CONNECTING
                } else {
                    LiveConnectionState.RETRYING
                }
                val closed = CompletableDeferred<Throwable?>()
                val namespaceConnected = CompletableDeferred<Unit>()
                val currentSocket = socketFactory.connect(socketListener(closed, namespaceConnected))
                socket = currentSocket
                val watchdog = scope.launch {
                    delay(handshakeTimeoutMillis)
                    if (!namespaceConnected.isCompleted) {
                        currentSocket.close(1000, "socket.io handshake timeout")
                        closed.complete(IOException("bazarr socket.io handshake timed out"))
                    }
                }
                closed.await()
                watchdog.cancel()
                socket = null
                if (!running.get()) break
                if (namespaceConnected.isCompleted) {
                    handshakeFailures = 0
                } else {
                    handshakeFailures++
                    if (handshakeFailures >= MAX_HANDSHAKE_FAILURES) {
                        runPollingFallback()
                        break
                    }
                }
                _state.value = LiveConnectionState.RETRYING
                delay(backoff.delayForAttempt(attempt))
                attempt++
            }
        } finally {
            _state.value = LiveConnectionState.DISCONNECTED
        }
    }

    private fun socketListener(
        closed: CompletableDeferred<Throwable?>,
        namespaceConnected: CompletableDeferred<Unit>,
    ): TextSocketListener = object : TextSocketListener {
        override fun onOpen() {}

        override fun onMessage(text: String) {
            val current = socket ?: return
            for (frame in EngineIo.parseAll(text)) {
                when (frame) {
                    is EngineIoFrame.Open -> current.send(EngineIo.SOCKET_IO_CONNECT)

                    EngineIoFrame.Ping -> current.send(EngineIo.ENGINE_IO_PONG)

                    EngineIoFrame.SocketIoConnected -> {
                        // The hub only reports liveness through state once the
                        // socket.io namespace is acked; that is when events flow.
                        _state.value = LiveConnectionState.CONNECTED
                        namespaceConnected.complete(Unit)
                    }

                    is EngineIoFrame.SocketIoEvent -> handleEvent(frame)

                    EngineIoFrame.SocketIoDisconnected -> closed.complete(
                        IOException("bazarr socket.io namespace disconnected"),
                    )

                    is EngineIoFrame.SocketIoConnectError -> closed.complete(
                        IOException("bazarr socket.io refused the handshake: " + frame.reason),
                    )

                    else -> {}
                }
            }
        }

        override fun onFailure(error: Throwable) {
            closed.complete(error)
        }

        override fun onClosed(code: Int, reason: String) {
            closed.complete(IOException("bazarr socket closed: $code $reason"))
        }
    }

    private fun handleEvent(frame: EngineIoFrame.SocketIoEvent) {
        ServarrEventMapper.map(serverId, appType, frame.name, actionOf(frame.payloadJson))
            ?.let(_events::tryEmit)
    }

    private fun actionOf(payloadJson: String?): String? {
        val payload = payloadJson ?: return null
        val element = runCatching { ArrJson.parseToJsonElement(payload) }.getOrNull() ?: return null
        val primitive = (element as? JsonObject)?.get("action") as? JsonPrimitive ?: return null
        return primitive.contentOrNull
    }

    /**
     * Blanket invalidation on a timer. Repositories' refresh cost is the price
     * of not speaking socket.io to a server that refuses the handshake.
     */
    private suspend fun runPollingFallback() {
        _state.value = LiveConnectionState.CONNECTED
        while (currentCoroutineContext().isActive && running.get()) {
            _events.tryEmit(
                ArrEvent.EntitiesChanged(serverId, entity = POLL_ENTITY_MOVIE, action = null),
            )
            _events.tryEmit(
                ArrEvent.EntitiesChanged(serverId, entity = POLL_ENTITY_SERIES, action = null),
            )
            delay(pollPeriodMillis)
        }
    }

    override suspend fun stop() {
        if (!running.compareAndSet(true, false)) return
        try {
            socket?.close(1000, "client stopped")
        } finally {
            loopJob?.cancel()
        }
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 64

        /** Two strikes, then poll: one flaky attempt must not downgrade forever. */
        const val MAX_HANDSHAKE_FAILURES = 2
        const val DEFAULT_POLL_PERIOD_MILLIS = 60_000L
        const val DEFAULT_HANDSHAKE_TIMEOUT_MILLIS = 10_000L
        const val POLL_ENTITY_MOVIE = "movie"
        const val POLL_ENTITY_SERIES = "series"
    }
}
