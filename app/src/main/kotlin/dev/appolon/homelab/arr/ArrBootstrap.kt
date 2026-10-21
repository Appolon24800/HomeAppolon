package dev.appolon.homelab.arr

import dev.appolon.homelab.arr.net.ProxyConfig
import dev.appolon.homelab.arr.servers.net.QueuedActionExecutor
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import dev.appolon.homelab.arr.feature.sonarr.SonarrPollAdapter
import dev.appolon.homelab.arr.feature.radarr.RadarrPollAdapter
import dev.appolon.homelab.arr.feature.prowlarr.ProwlarrPollAdapter
import dev.appolon.homelab.arr.feature.lidarr.LidarrPollAdapter
import dev.appolon.homelab.arr.feature.bazarr.BazarrPollAdapter
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.Keep
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import dev.appolon.homelab.arr.sync.ArrEvent
import dev.appolon.homelab.arr.sync.live.LiveConnectionManager
import dev.appolon.homelab.arr.sync.live.LiveServerSpec
import dev.appolon.homelab.arr.sync.scheduler.ArrNotificationChannels
import dev.appolon.homelab.arr.sync.scheduler.SyncBootstrap
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.shell.ArrShell
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.ShellServer
import dev.appolon.homelab.arr.ui.theme.ArrTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Process-wide holder for the native arr client. One [ArrRuntime] per process:
 * the data graph, route registry, merged shell content, and live connection
 * manager are singletons, and DataStore tolerates a single writer per file.
 */
@Keep
object ArrBootstrap {

    fun configureSessionCookies() {
        dev.appolon.homelab.arr.net.ArrNetworkDefaults.cookieSource = WebViewCookieSource()
    }

    @Volatile
    private var runtime: ArrRuntime? = null

    internal fun runtime(context: Context): ArrRuntime =
        runtime ?: synchronized(this) {
            runtime ?: ArrRuntime(context.applicationContext).also { runtime = it }
        }

    /**
     * Channel registration, poll scheduling, and graph warm-up; called through
     * [ArrIntegration.onAppStart].
     */
    internal fun onAppStart(context: Context) {
        val app = context.applicationContext
        app.getSystemService(NotificationManager::class.java)?.let { manager ->
            ArrNotificationChannels.ensureCreated(app, manager)
        }
        // Schedules the unique periodic poll; KEEP leaves an existing schedule.
        SyncBootstrap.initialize(app)
        runtime(app)
    }

    /**
     * Reflective target [SyncBootstrap.ensureDefaultAdapters] resolves when a
     * process WorkManager cold-started straight into a poll never ran
     * [onAppStart]. `@JvmStatic` plus the object's `@Keep` keep the entry
     * reachable and named in minified builds.
     */
    @Keep
    @JvmStatic
    fun registerSyncAdaptersForColdStart(context: Context) {
        val coldGraph = SyncBootstrap.dataGraph(context)
        val coldScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        registerDefaultSyncAdapters(
            graph = coldGraph,
            clientFactory = DefaultArrClientFactory(
                globalProxy = globalProxySnapshot(coldGraph, coldScope),
                cookieSource = WebViewCookieSource(),
            ),
        )
    }
}


/** Bridges the app WebView's cookie jar into arr API clients, same-origin only. */
private class WebViewCookieSource : dev.appolon.homelab.arr.net.CookieSource {
    override fun cookiesFor(url: String): List<okhttp3.Cookie> = runCatching {
        val manager = android.webkit.CookieManager.getInstance()
        val header = manager.getCookie(url) ?: return emptyList()
        val host = java.net.URI(url).host ?: return emptyList()
        header.split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            okhttp3.Cookie.Builder()
                .name(part.substring(0, idx).trim())
                .value(part.substring(idx + 1).trim())
                .domain(host)
                .build()
        }
    }.getOrDefault(emptyList())
}

/**
 * Cheap cached snapshot of the global default proxy: a collector keeps a
 * StateFlow warm and the factory's non-suspend provider reads its value, so
 * proxy resolution costs nothing per request while changes still rebuild
 * cached clients through the factory's fingerprint.
 */
private fun globalProxySnapshot(graph: ArrDataGraph, scope: CoroutineScope): () -> ProxyConfig? {
    val snapshot = MutableStateFlow<ProxyConfig?>(null)
    scope.launch {
        runCatching { graph.settings.observeDefaultProxy().collect { snapshot.value = it } }
    }
    return { snapshot.value }
}

/**
 * The five per-app poll adapters, built on the process graph. Both paths that
 * need them call this: app start (through [ArrRuntime]) and the worker's cold
 * start (through [ArrBootstrap.registerSyncAdaptersForColdStart]).
 */
internal fun registerDefaultSyncAdapters(graph: ArrDataGraph, clientFactory: ArrClientFactory) {
    SyncBootstrap.registerAdapter(AppType.RADARR, RadarrPollAdapter(graph.secrets, graph.entityStore, clientFactory))
    SyncBootstrap.registerAdapter(AppType.SONARR, SonarrPollAdapter(graph.secrets, graph.entityStore, clientFactory))
    SyncBootstrap.registerAdapter(AppType.LIDARR, LidarrPollAdapter(graph.secrets, graph.entityStore, clientFactory))
    SyncBootstrap.registerAdapter(AppType.PROWLARR, ProwlarrPollAdapter(graph.secrets, graph.entityStore, clientFactory))
    SyncBootstrap.registerAdapter(AppType.BAZARR, BazarrPollAdapter(graph.secrets, graph.entityStore, clientFactory))
}

/**
 * Owns the arr singletons and their background scope. Nothing here touches
 * disk until first access: the graph's members are lazy, and the feature
 * wiring (which fans them into shell contents) waits for the first
 * [ArrHost] composition through [content].
 */
internal class ArrRuntime(context: Context) {
    val graph: ArrDataGraph = SyncBootstrap.dataGraph(context)
    val clientFactory: ArrClientFactory = DefaultArrClientFactory(cookieSource = WebViewCookieSource())
    val registry: ArrRouteRegistry = ArrRouteRegistry()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Deferred until the first [ArrHost] composition; see the class KDoc. */
    private val wiring by lazy {
        ArrFeatureWiring(
            graph = graph,
            registry = registry,
            clientFactory = clientFactory,
            scope = scope,
            liveEvents = live.events,
        )
    }

    /** Merged shell content for every configured app. */
    val content: ArrShellContent get() = wiring.content

    /** Switcher display data, kept live from the server registry. */
    val shellServers: MutableStateFlow<List<ShellServer>> get() = wiring.shellServers

    private val connectivity = connectivityFlow(context)

    private val live = LiveConnectionManager(
        scope = scope,
        connectivity = connectivity,
        specs = { graph.registry.servers().mapNotNull(::liveSpecOf) },
        clientFactory = clientFactory,
    )

    /** Every live event from every server; the wiring scopes it per feature. */
    val liveEvents: SharedFlow<ArrEvent> get() = live.events

    init {
        dev.appolon.homelab.arr.net.ArrNetworkDefaults.globalProxy = globalProxySnapshot(graph, scope)
        // Reconnect = replay time: whenever connectivity returns, drain the
        // offline action queue once for every registered server.
        scope.launch {
            connectivity
                .drop(1) // ignore the initial state emission
                .filter { it }
                .collect {
                    runCatching {
                        graph.actionQueue.replay { action ->
                            QueuedActionExecutor(graph.registry, graph.secrets).execute(action)
                        }
                    }
                }
        }
    }

    init {
        // Background sync adapters: one per app type, sharing the graph and client factory.
        registerDefaultSyncAdapters(graph, clientFactory)
        SyncBootstrap.installDefaultAdapterRegistrar {
            registerDefaultSyncAdapters(graph, clientFactory)
        }
    }

    init {
        scope.launch { graph.initialize() }
    }

    /**
     * Starts the SignalR/socket.io connections for every configured server.
     * [LiveConnectionManager.start] schedules the first reconcile itself; a
     * second refresh here would race it and could install duplicates.
     */
    fun startLive() {
        live.start()
    }

    /** Stops every live connection; [startLive] can bring them back later. */
    fun stopLive() {
        scope.launch { live.stop() }
    }

    private fun liveSpecOf(server: ArrServer): LiveServerSpec? {
        val endpoint = endpointOf(server) ?: return null
        return LiveServerSpec(endpoint = endpoint, appType = server.type)
    }

    /** Decrypts one server's credentials; null (never an exception) when the key is unusable. */
    internal fun endpointOf(server: ArrServer): ServerEndpoint? = try {
        ServerEndpoint(
            serverId = server.id,
            baseUrl = server.baseUrl,
            apiKey = graph.secrets.decrypt(server.apiKeyEncrypted),
            basicAuthUser = server.basicAuthUser,
            basicAuthPassword = server.basicAuthPasswordEncrypted?.let { graph.secrets.decrypt(it) },
            proxy = server.proxy,
            trust = server.trust ?: TrustSpec.System,
        )
    } catch (t: Throwable) {
        null
    }
}

/**
 * Full-screen host for the native arr shell. The dashboard composes this for
 * the server a service tap resolved to; [onExit] returns to the dashboard and
 * is wired to the shell's back-at-root behavior. Live connections start when
 * the host enters composition and stop when it leaves.
 */
@Composable
fun ArrHost(
    serverId: ServerId,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    openGeneration: Long = 0L,
) {
    val context = LocalContext.current
    val runtime = remember(context) { ArrBootstrap.runtime(context) }
    val servers by runtime.shellServers.collectAsStateWithLifecycle()

    // Live sockets live exactly as long as the host does.
    DisposableEffect(runtime) {
        runtime.startLive()
        onDispose { runtime.stopLive() }
    }

    // The shell's server choice rides ArrShell's focus parameters: first
    // open, re-open, and server switches all flow through the switcher's
    // idempotent navigation policy there.
    val base = runtime.content

    ArrTheme {
        ArrShell(
            servers = servers,
            onServerSelected = {},
            onExit = onExit,
            modifier = modifier,
            registry = runtime.registry,
            content = base,
            focusServer = serverId,
            focusGeneration = openGeneration,
        )
    }
}

/**
 * Network availability for the live connection manager. Emits the current
 * state immediately, then only real transitions, so a chatty listener cannot
 * churn sockets.
 */
private fun connectivityFlow(context: Context): Flow<Boolean> = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    if (manager == null) {
        trySend(true)
        awaitClose { }
        return@callbackFlow
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(true)
        }

        override fun onLost(network: Network) {
            trySend(manager.activeNetwork != null)
        }
    }
    trySend(manager.activeNetwork != null)
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()
