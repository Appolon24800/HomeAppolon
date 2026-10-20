package dev.appolon.homelab.arr

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
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
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Process-wide holder for the native arr client. One [ArrRuntime] per process:
 * the data graph, route registry, merged shell content, and live connection
 * manager are singletons, and DataStore tolerates a single writer per file.
 */
object ArrBootstrap {

    @Volatile
    private var runtime: ArrRuntime? = null

    internal fun runtime(context: Context): ArrRuntime =
        runtime ?: synchronized(this) {
            runtime ?: ArrRuntime(context.applicationContext).also { runtime = it }
        }

    /** Channel registration plus graph warm-up; called through [ArrIntegration.onAppStart]. */
    internal fun onAppStart(context: Context) {
        val app = context.applicationContext
        app.getSystemService(NotificationManager::class.java)?.let { manager ->
            ArrNotificationChannels.ensureCreated(app, manager)
        }
        runtime(app)
    }
}

/**
 * Owns the arr singletons and their background scope. Nothing here touches
 * disk until first access; construction is cheap enough to trigger lazily
 * from the first dashboard tap or app start.
 */
internal class ArrRuntime(context: Context) {
    val graph: ArrDataGraph = SyncBootstrap.dataGraph(context)
    val clientFactory: ArrClientFactory = DefaultArrClientFactory()
    val registry: ArrRouteRegistry = ArrRouteRegistry()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wiring = ArrFeatureWiring(
        graph = graph,
        registry = registry,
        clientFactory = clientFactory,
        scope = scope,
    )

    /** Merged shell content for every configured app. */
    val content: ArrShellContent = wiring.content

    /** Switcher display data, kept live from the server registry. */
    val shellServers = wiring.shellServers

    private val live = LiveConnectionManager(
        scope = scope,
        connectivity = connectivityFlow(context),
        specs = { graph.registry.servers().mapNotNull(::liveSpecOf) },
        clientFactory = clientFactory,
    )

    init {
        scope.launch { graph.initialize() }
    }

    /** Starts the SignalR/socket.io connections for every configured server. */
    fun startLive() {
        live.start()
        scope.launch { live.refresh() }
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
fun ArrHost(serverId: ServerId, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val runtime = remember(context) { ArrBootstrap.runtime(context) }
    val servers by runtime.shellServers.collectAsStateWithLifecycle()

    // Live sockets live exactly as long as the host does.
    DisposableEffect(runtime) {
        runtime.startLive()
        onDispose { runtime.stopLive() }
    }

    // The shell always starts on the server list; the dashboard tap already
    // chose a server, so navigate to it once the shell's navigator exists.
    // The list destination is the first composed screen, which is why the
    // one-shot trigger rides inside its content slot.
    var pendingInitial by remember(serverId) { mutableStateOf<ServerId?>(serverId) }
    val base = runtime.content
    val content = if (pendingInitial == null) {
        base
    } else {
        val target = serverId
        val hasServerHome = base.serverHome != null
        ArrShellContent(
            serverList = { route ->
                val navigator = LocalArrShellNavigator.current
                LaunchedEffect(target, servers) {
                    // Wait for the registry emission: a shell opened straight
                    // from a tap can compose before the collector lands.
                    val app: AppType = servers.firstOrNull { it.id == target }?.appType
                        ?: return@LaunchedEffect
                    val initial: ArrRoute = if (hasServerHome) {
                        ArrRoute.ServerHome(target)
                    } else {
                        // No feature bundle fills the server-home slot yet,
                        // so land on the app's library screen instead.
                        ArrRoute.Library(target, app)
                    }
                    navigator.open(initial)
                    pendingInitial = null
                }
                base.serverList?.invoke(route)
            },
            serverHome = base.serverHome,
            library = base.library,
            detail = base.detail,
            add = base.add,
            queue = base.queue,
            calendar = base.calendar,
            settings = base.settings,
            feature = base.feature,
        )
    }

    ArrTheme {
        ArrShell(
            servers = servers,
            onServerSelected = {},
            onExit = onExit,
            modifier = modifier,
            registry = runtime.registry,
            content = content,
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
