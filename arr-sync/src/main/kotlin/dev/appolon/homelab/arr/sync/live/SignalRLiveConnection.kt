package dev.appolon.homelab.arr.sync.live

import com.microsoft.signalr.Action1
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.TransportEnum
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.sync.ArrEvent
import dev.appolon.homelab.arr.sync.LiveConnectionState
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
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
import okhttp3.Credentials
import okhttp3.OkHttpClient

/**
 * The slice of [com.microsoft.signalr.HubConnection] this module needs, as a
 * public interface so pure-JVM tests can drive the reconnect loop against a
 * fake hub instead of the concrete (gson-wired) client, while
 * [SignalRLiveConnection] stays constructible with its own default port.
 */
interface HubPort {
    /** Registers the handler for the hub's single broadcast method. */
    fun onMessage(handler: (name: String?, action: String?) -> Unit)

    /** Fires when the transport dies, and again after a clean stop. */
    fun onClosed(handler: (error: Throwable?) -> Unit)

    /** Completes once the handshake finished; fails when connecting was refused. */
    suspend fun start()

    suspend fun stop()
}

/**
 * Production [HubPort] over the Microsoft SignalR Java client. Websocket
 * transport only: Servarr deployments behind reverse proxies do reliably well
 * on upgrades and badly on long polling.
 */
internal class SignalRHubPort(
    endpoint: ServerEndpoint,
    httpClient: OkHttpClient,
) : HubPort {
    private val hub: HubConnection = HubConnectionBuilder.create(
        signalRHubUrl(endpoint.baseUrl, endpoint.apiKey),
    )
        .withTransport(TransportEnum.WEBSOCKETS)
        .withHeaders(identityHeaders(endpoint))
        .setHttpClientBuilderCallback { builder -> builder.mirrorNetworkPolicyOf(httpClient) }
        .withServerTimeout(SERVER_TIMEOUT_MILLIS)
        .withKeepAliveInterval(KEEP_ALIVE_INTERVAL_MILLIS)
        .build()

    override fun onMessage(handler: (String?, String?) -> Unit) {
        // The client hands the payload to gson for the requested target type.
        // Map is the only compile-visible type that survives a JSON object
        // without putting gson itself on this module's compile classpath.
        @Suppress("UNCHECKED_CAST")
        val payloadType = Map::class.java as Class<Map<String, Any?>>
        hub.on(
            RECEIVE_MESSAGE_METHOD,
            Action1 { payload: Map<String, Any?> ->
                handler(nameOf(payload), actionOf(payload))
            },
            payloadType,
        )
    }

    override fun onClosed(handler: (Throwable?) -> Unit) {
        hub.onClosed { error -> handler(error) }
    }

    override suspend fun start() {
        hub.start().awaitCompletion()
    }

    override suspend fun stop() {
        hub.stop().awaitCompletion()
    }

    private fun nameOf(payload: Map<String, Any?>): String? = payload["name"] as? String

    private fun actionOf(payload: Map<String, Any?>): String? =
        (payload["body"] as? Map<*, *>)?.get("action") as? String

    private companion object {
        const val SERVER_TIMEOUT_MILLIS = 30_000L
        const val KEEP_ALIVE_INTERVAL_MILLIS = 15_000L

        /** The single hub method every Servarr app broadcasts on. */
        const val RECEIVE_MESSAGE_METHOD = "receiveMessage"
    }
}

/**
 * Live connection for the four Servarr apps (Radarr, Sonarr, Lidarr,
 * Prowlarr). Negotiates and connects to the `/signalr` hub with websocket
 * transport, maps `receiveMessage` payloads onto [ArrEvent] through
 * [ServarrEventMapper], and reconnects with [ExponentialBackoff] until
 * [stop]. The connection never emits while DISCONNECTED, so a clean stop is
 * also a clean event cutoff.
 */
class SignalRLiveConnection(
    private val spec: LiveServerSpec,
    private val scope: CoroutineScope,
    httpClient: OkHttpClient = OkHttpClient(),
    private val backoff: ExponentialBackoff = ExponentialBackoff(),
    hubPortFactory: (() -> HubPort)? = null,
) : LiveConnection {
    private val hubPort: HubPort = hubPortFactory?.invoke() ?: SignalRHubPort(spec.endpoint, httpClient)

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

    // Registered once, not per start(): the underlying hub stacks subscriptions,
    // and a start/stop/start cycle must not deliver each message twice.
    @Volatile
    private var closedSignal = CompletableDeferred<Throwable?>()

    init {
        hubPort.onMessage { name, action ->
            ServarrEventMapper.map(serverId, appType, name, action)?.let(_events::tryEmit)
        }
        hubPort.onClosed { error -> if (running.get()) closedSignal.complete(error) }
    }

    override suspend fun start() {
        if (!running.compareAndSet(false, true)) return
        loopJob = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        var attempt = 0
        try {
            while (currentCoroutineContext().isActive && running.get()) {
                _state.value = if (attempt == 0) {
                    LiveConnectionState.CONNECTING
                } else {
                    LiveConnectionState.RETRYING
                }
                closedSignal = CompletableDeferred()
                try {
                    hubPort.start()
                    _state.value = LiveConnectionState.CONNECTED
                    attempt = 0
                    closedSignal.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Refused, timed out, or handshake-rejected; retry below.
                }
                if (!running.get()) break
                _state.value = LiveConnectionState.RETRYING
                delay(backoff.delayForAttempt(attempt))
                attempt++
            }
        } finally {
            _state.value = LiveConnectionState.DISCONNECTED
        }
    }

    override suspend fun stop() {
        if (!running.compareAndSet(true, false)) return
        try {
            hubPort.stop()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Stopping a hub that never opened can still throw; it stays stopped.
        } finally {
            loopJob?.cancel()
        }
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 64
    }
}

/**
 * Servarr serves its hub at `{base}/signalr` and authenticates it like the
 * REST API. The key rides both the header and the query string: the header is
 * what the API middleware validates, the query string is the standard SignalR
 * token channel and survives proxies that rewrite upgrade requests.
 */
internal fun signalRHubUrl(baseUrl: String, apiKey: String): String =
    baseUrl.trimEnd('/') + "/signalr?access_token=" + URLEncoder.encode(apiKey, "UTF-8")

/**
 * Stamps the server's identity on both the negotiate POST and the websocket
 * upgrade. Mirrors [dev.appolon.homelab.arr.net] conventions so the hub sees
 * exactly the same client the REST calls do.
 */
private fun identityHeaders(endpoint: ServerEndpoint): Map<String, String> {
    val headers = linkedMapOf(
        "X-Api-Key" to endpoint.apiKey,
        "User-Agent" to USER_AGENT,
    )
    val basicUser = endpoint.basicAuthUser
    val basicPassword = endpoint.basicAuthPassword
    if (!basicUser.isNullOrEmpty() && !basicPassword.isNullOrEmpty()) {
        headers["Authorization"] = Credentials.basic(basicUser, basicPassword)
    }
    return headers
}

/**
 * Copies the proxy and TLS policy of the per-server OkHttp client onto the
 * SignalR client's fresh builder. Identity headers deliberately do not come
 * along: they ride [HubConnectionBuilder] headers, so negotiate and the
 * upgrade request get exactly one copy each.
 */
private fun OkHttpClient.Builder.mirrorNetworkPolicyOf(client: OkHttpClient) {
    client.proxy?.let { proxy(it) }
    client.sslSocketFactory?.let { factory ->
        client.x509TrustManager?.let { trustManager -> sslSocketFactory(factory, trustManager) }
    }
    hostnameVerifier(client.hostnameVerifier)
    client.proxyAuthenticator?.let { proxyAuthenticator(it) }
}

private const val USER_AGENT = "HomelabArr/1.0 (android)"
