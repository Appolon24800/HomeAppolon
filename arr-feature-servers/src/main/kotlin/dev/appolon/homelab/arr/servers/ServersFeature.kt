package dev.appolon.homelab.arr.servers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.servers.data.ServersSettingsStore
import dev.appolon.homelab.arr.servers.net.QueuedActionExecutor
import dev.appolon.homelab.arr.servers.net.ServerConnectionTester
import dev.appolon.homelab.arr.servers.notify.NotificationPreview
import dev.appolon.homelab.arr.servers.ui.NotificationPrefsScreen
import dev.appolon.homelab.arr.servers.ui.ProxySettingsScreen
import dev.appolon.homelab.arr.servers.ui.ServerEditSheet
import dev.appolon.homelab.arr.servers.ui.ServerListScreen
import dev.appolon.homelab.arr.servers.ui.SyncStatusScreen
import dev.appolon.homelab.arr.servers.vm.NotificationPrefsViewModel
import dev.appolon.homelab.arr.servers.vm.ProxySettingsViewModel
import dev.appolon.homelab.arr.servers.vm.ServerEditViewModel
import dev.appolon.homelab.arr.servers.vm.ServerListViewModel
import dev.appolon.homelab.arr.servers.vm.SyncStatusViewModel
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext

/**
 * Everything the arr shell needs from this module: nested route patterns,
 * open helpers, and the slot composables for [ArrShellContent]. The
 * integration layer calls [register] once before composing the shell and
 * passes [serversShellContent] (or the individual destinations) into it.
 */
object ServersFeature {
    const val ROUTE_OWNER = "servers"

    const val EDIT_PATTERN = "edit"
    const val EDIT_SERVER_PATTERN = "edit/{serverId}"
    const val PROXY_PATTERN = "proxy"
    const val NOTIFICATIONS_PATTERN = "notifications"
    const val SYNC_PATTERN = "sync"

    /** All nested patterns this module owns, for one-time registration. */
    val PATTERNS = listOf(
        EDIT_PATTERN,
        EDIT_SERVER_PATTERN,
        PROXY_PATTERN,
        NOTIFICATIONS_PATTERN,
        SYNC_PATTERN,
    )

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openEdit(navigator: ArrShellNavigator, serverId: ServerId?) {
        val path = serverId?.let { id ->
            fillPattern(EDIT_SERVER_PATTERN, mapOf("serverId" to id.value))
        } ?: EDIT_PATTERN
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }

    fun openProxy(navigator: ArrShellNavigator) {
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, PROXY_PATTERN))
    }

    fun openNotifications(navigator: ArrShellNavigator) {
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, NOTIFICATIONS_PATTERN))
    }

    fun openSync(navigator: ArrShellNavigator) {
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, SYNC_PATTERN))
    }
}

/**
 * Ready-made shell content: the server list on the shell's start destination
 * and this module's nested routes in the feature slot. Foreign feature
 * owners render the shell's "not available" empty state so a partial
 * integration cannot show a blank screen.
 */
fun serversShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    serverList = { route -> ServerListDestination(route, graph) },
    feature = { route -> ServersFeatureSlot(route, graph) },
)

/** The shell's start destination: the server list. */
@Composable
fun ServerListDestination(
    route: ArrRoute.ServerList,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val tester = remember { ServerConnectionTester() }
    val vm: ServerListViewModel = viewModel(key = "server-list") {
        ServerListViewModel(
            registry = graph.registry,
            secrets = graph.secrets,
            tester = tester,
            deleteEverywhere = { graph.deleteServer(it) },
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()
    val navigator = LocalArrShellNavigator.current

    ServerListScreen(
        lce = lce,
        health = state.health,
        probing = state.probing,
        onAdd = { },
        onEdit = { },
        onMoveUp = vm::moveUp,
        onMoveDown = vm::moveDown,
        onDelete = vm::delete,
        onOpenProxy = { ServersFeature.openProxy(navigator) },
        onOpenNotifications = { ServersFeature.openNotifications(navigator) },
        onOpenSync = { ServersFeature.openSync(navigator) },
        onRetry = vm::probeAll,
        modifier = modifier,
    )
}

/** Feature-slot entry: renders this module's routes, empty state for others. */
@Composable
fun ServersFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != ServersFeature.ROUTE_OWNER) {
        EmptyState(
            title = foreignTitle(),
            message = foreignMessage(route.owner),
            modifier = modifier,
        )
        return
    }
    val segments = route.path.split('/')
    val navigator = LocalArrShellNavigator.current
    val onBack: () -> Unit = { navigator.back() }
    when {
        matchRoutePattern(ServersFeature.EDIT_SERVER_PATTERN, segments) != null -> {
            val id = matchRoutePattern(ServersFeature.EDIT_SERVER_PATTERN, segments)
                ?.getValue("serverId")
            ServerEditDestination(
                serverId = id?.let(::ServerId),
                graph = graph,
                onBack = onBack,
                modifier = modifier,
            )
        }

        matchRoutePattern(ServersFeature.EDIT_PATTERN, segments) != null ->
            ServerEditDestination(
                serverId = null,
                graph = graph,
                onBack = onBack,
                modifier = modifier,
            )

        matchRoutePattern(ServersFeature.PROXY_PATTERN, segments) != null ->
            ProxySettingsDestination(graph, onBack, modifier)

        matchRoutePattern(ServersFeature.NOTIFICATIONS_PATTERN, segments) != null ->
            NotificationPrefsDestination(graph, onBack, modifier)

        matchRoutePattern(ServersFeature.SYNC_PATTERN, segments) != null ->
            SyncStatusDestination(graph, onBack, modifier)

        else -> EmptyState(
            title = foreignTitle(),
            message = foreignMessage(route.owner),
            modifier = modifier,
        )
    }
}

@Composable
private fun foreignTitle(): String =
    androidx.compose.ui.res.stringResource(
        dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_title,
    )

@Composable
private fun foreignMessage(owner: String): String =
    androidx.compose.ui.res.stringResource(
        R.string.arr_servers_foreign_message,
        owner,
    )

/** Full-screen add/edit editor for new servers or [serverId]. */
@Composable
fun ServerEditDestination(
    serverId: ServerId?,
    graph: ArrDataGraph,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tester = remember { ServerConnectionTester() }
    val vm: ServerEditViewModel = viewModel(
        key = "server-edit-" + (serverId?.value ?: "new"),
    ) {
        ServerEditViewModel(
            registry = graph.registry,
            secrets = graph.secrets,
            tester = tester,
            serverId = serverId,
        )
    }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    ServerEditSheet(
        state = state,
        onUpdate = vm::update,
        onTest = vm::testConnection,
        onSave = vm::save,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Global default proxy plus per-server overrides. */
@Composable
fun ProxySettingsDestination(
    graph: ArrDataGraph,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ProxySettingsViewModel = viewModel(key = "server-proxy") {
        ProxySettingsViewModel(settings = graph.settings, registry = graph.registry)
    }
    val state by vm.state.collectAsState()
    ProxySettingsScreen(
        state = state,
        onUpdateGlobal = vm::updateGlobal,
        onSetOverride = vm::setOverride,
        onUpdateServer = vm::updateServer,
        onSave = vm::save,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Per-server channel toggles, kill switch, preview notification. */
@Composable
fun NotificationPrefsDestination(
    graph: ArrDataGraph,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: NotificationPrefsViewModel = viewModel(key = "server-notifications") {
        NotificationPrefsViewModel(
            settings = graph.settings,
            prefs = ServersSettingsStore(graph.entityStore),
            registry = graph.registry,
        )
    }
    val state by vm.state.collectAsState()
    NotificationPrefsScreen(
        state = state,
        onSetKillSwitch = vm::setKillSwitch,
        onSetChannel = vm::setEnabled,
        onPreview = { serverName -> NotificationPreview.post(context, serverName) },
        onBack = onBack,
        modifier = modifier,
    )
}

/** Pending and dead-letter actions with retry and discard. */
@Composable
fun SyncStatusDestination(
    graph: ArrDataGraph,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val executor = remember(graph) { QueuedActionExecutor(graph.registry, graph.secrets) }
    val vm: SyncStatusViewModel = viewModel(key = "server-sync") {
        SyncStatusViewModel(
            queue = graph.actionQueue,
            deadLetterSource = { graph.actionQueue.deadLetters() },
            executor = executor::execute,
        )
    }
    val state by vm.state.collectAsState()
    SyncStatusScreen(
        state = state,
        onRetryAll = vm::retryAll,
        onRetryOne = vm::retryOne,
        onDiscard = vm::discard,
        onBack = onBack,
        onRetry = vm::refresh,
        modifier = modifier,
    )
}
