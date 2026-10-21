package dev.appolon.homelab.arr.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.R
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.nav.ArrBackDirective
import dev.appolon.homelab.arr.ui.nav.ArrNestedRoute
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.ArrRoutes
import dev.appolon.homelab.arr.ui.nav.ServerSwitchDirective
import dev.appolon.homelab.arr.ui.nav.arrBackDirective
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.nestedNavPattern
import dev.appolon.homelab.arr.ui.nav.patternParams
import dev.appolon.homelab.arr.ui.nav.serverSwitchDirective
import kotlinx.coroutines.flow.first

/**
 * Navigation surface for nested arr screens. Screens read it through
 * [LocalArrShellNavigator]; the shell provides the NavHost-backed implementation.
 */
interface ArrShellNavigator {
    /** Pushes any route. Feature routes navigate by their opaque encoded path. */
    fun open(route: ArrRoute)

    /**
     * Opens a server's home applying the back-stack switch policy: pops to the
     * server list (saving state) instead of stacking servers on each other.
     */
    fun openServer(serverId: ServerId)

    /** Pops one entry. Returns false when the stack is already at its root. */
    fun back(): Boolean

    companion object {
        /** Fallback used outside a shell (previews, tests); every call is a no-op. */
        val Noop: ArrShellNavigator = object : ArrShellNavigator {
            override fun open(route: ArrRoute) {}
            override fun openServer(serverId: ServerId) {}
            override fun back(): Boolean = false
        }
    }
}

/** Navigator for the closest enclosing [ArrShell]. */
val LocalArrShellNavigator = compositionLocalOf<ArrShellNavigator> { ArrShellNavigator.Noop }

/**
 * Composable content slots for each shell destination. A null slot renders the
 * shell's "screen not available" placeholder, so partially wired integrations
 * still work. Slots stay stateless; state belongs in the feature's ViewModel.
 */
class ArrShellContent(
    val serverList: (@Composable (ArrRoute.ServerList) -> Unit)? = null,
    val serverHome: (@Composable (ArrRoute.ServerHome) -> Unit)? = null,
    val library: (@Composable (ArrRoute.Library) -> Unit)? = null,
    val detail: (@Composable (ArrRoute.Detail) -> Unit)? = null,
    val add: (@Composable (ArrRoute.Add) -> Unit)? = null,
    val queue: (@Composable (ArrRoute.Queue) -> Unit)? = null,
    val calendar: (@Composable (ArrRoute.Calendar) -> Unit)? = null,
    val settings: (@Composable (ArrRoute.Settings) -> Unit)? = null,
    val feature: (@Composable (ArrRoute.Feature) -> Unit)? = null,
)

/**
 * The arr navigation host: a NavHost over [ArrRoute] with a bottom-docked server
 * switcher and a settings entry. Back at the stack root leaves the shell through
 * [onExit] (the dashboard owns what happens then); anywhere else pops normally.
 *
 * The shell renders only what callers provide via [content]; it has no dependency
 * on :arr-data or any feature module. [servers] is display data only.
 */
@Composable
fun ArrShell(
    servers: List<ShellServer>,
    onServerSelected: (ServerId) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    registry: ArrRouteRegistry = remember { ArrRouteRegistry() },
    content: ArrShellContent = ArrShellContent(),
    focusServer: ServerId? = null,
    focusGeneration: Long = 0L,
) {
    val navController = rememberNavController()
    val navigator = remember(navController, registry) {
        NavControllerArrShellNavigator(navController, registry)
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.toArrRoute(registry)
    val currentServerId = currentRoute?.let(ArrRoutes::serverIdOf)
    val currentServer = servers.firstOrNull { it.id == currentServerId }

    // The host's chosen server: navigate to it whenever a new focus request
    // arrives (first open or a re-open of the same server). This must live
    // above the NavHost: a destination-scoped effect dies with its screen,
    // and the server list is only composed at the stack root. Membership is
    // awaited without re-keying on the list, so later registry changes never
    // snap the user back while they browse elsewhere.
    val currentServers by androidx.compose.runtime.rememberUpdatedState(servers)
    LaunchedEffect(focusServer, focusGeneration) {
        val target = focusServer ?: return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { currentServers.any { it.id == target } }
            .first { it }
        navigator.openServer(target)
    }

    BackHandler {
        when (arrBackDirective(navController.previousBackStackEntry != null)) {
            ArrBackDirective.PopStack -> navController.popBackStack()
            ArrBackDirective.ExitToDashboard -> onExit()
        }
    }

    Scaffold(
        modifier = modifier,
        // The shell adds no top bar, and every destination screen owns its
        // own scaffold with a top app bar that clears the status bar and
        // cutout. Default insets here would stack a second (or third) top
        // inset onto the screen's own, leaving a dead band above the bar.
        // The bottom switcher pads the navigation bar itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                ServerSwitcher(
                    servers = servers,
                    selectedId = currentServerId,
                    onServerClick = { server ->
                        onServerSelected(server.id)
                        navigator.openServer(server.id)
                    },
                    onSettingsClick = {
                        val target = currentServer
                        if (target != null) {
                            navigator.open(ArrRoute.Settings(target.id, target.appType))
                        } else {
                            navigator.open(ArrRoute.ServerList)
                        }
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(vertical = 4.dp),
                )
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalArrShellNavigator provides navigator) {
            NavHost(
                navController = navController,
                startDestination = ArrRoutes.SERVER_LIST_ROUTE,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                composable(ArrRoutes.SERVER_LIST_ROUTE) {
                    shellSlot(ArrRoute.ServerList, ArrRoutes.SERVER_LIST_ROUTE, content.serverList)
                }
                composable(ArrRoutes.SERVER_HOME_PATTERN) { entry ->
                    shellSlot(
                        entry.serverIdArg()?.let { ArrRoute.ServerHome(it) },
                        ArrRoutes.SERVER_HOME_PATTERN,
                        content.serverHome,
                    )
                }
                composable(ArrRoutes.LIBRARY_PATTERN) { entry ->
                    shellSlot(
                        entry.appRouteArgs()?.let { (id, app) -> ArrRoute.Library(id, app) },
                        ArrRoutes.LIBRARY_PATTERN,
                        content.library,
                    )
                }
                composable(ArrRoutes.DETAIL_PATTERN) { entry ->
                    shellSlot(
                        entry.detailArgs()?.let { (id, app, entityId) ->
                            ArrRoute.Detail(id, app, entityId)
                        },
                        ArrRoutes.DETAIL_PATTERN,
                        content.detail,
                    )
                }
                composable(ArrRoutes.ADD_PATTERN) { entry ->
                    shellSlot(
                        entry.appRouteArgs()?.let { (id, app) -> ArrRoute.Add(id, app) },
                        ArrRoutes.ADD_PATTERN,
                        content.add,
                    )
                }
                composable(ArrRoutes.QUEUE_PATTERN) { entry ->
                    shellSlot(
                        entry.appRouteArgs()?.let { (id, app) -> ArrRoute.Queue(id, app) },
                        ArrRoutes.QUEUE_PATTERN,
                        content.queue,
                    )
                }
                composable(ArrRoutes.CALENDAR_PATTERN) { entry ->
                    shellSlot(
                        entry.appRouteArgs()?.let { (id, app) -> ArrRoute.Calendar(id, app) },
                        ArrRoutes.CALENDAR_PATTERN,
                        content.calendar,
                    )
                }
                composable(ArrRoutes.SETTINGS_PATTERN) { entry ->
                    shellSlot(
                        entry.appRouteArgs()?.let { (id, app) -> ArrRoute.Settings(id, app) },
                        ArrRoutes.SETTINGS_PATTERN,
                        content.settings,
                    )
                }
                registry.nestedRoutes().forEach { nested ->
                    composable(route = nestedNavPattern(nested)) { entry ->
                        shellSlot(
                            entry.featureArgs(nested),
                            nestedNavPattern(nested),
                            content.feature,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrShellPlaceholder(pattern: String) {
    EmptyState(
        title = stringResource(R.string.arr_shell_unavailable_title),
        message = stringResource(R.string.arr_shell_unavailable_message, pattern),
    )
}

/** Renders a content slot when both route and slot exist, else the placeholder. */
@Composable
private fun <T : ArrRoute> shellSlot(
    route: T?,
    pattern: String,
    slot: (@Composable (T) -> Unit)?,
) {
    if (route != null && slot != null) {
        slot(route)
    } else {
        ArrShellPlaceholder(pattern)
    }
}

private class NavControllerArrShellNavigator(
    private val navController: NavHostController,
    private val registry: ArrRouteRegistry,
) : ArrShellNavigator {

    override fun open(route: ArrRoute) {
        navController.navigate(ArrRoutes.encode(route))
    }

    override fun openServer(serverId: ServerId) {
        val current = navController.currentBackStackEntry?.toArrRoute(registry)
            ?.let(ArrRoutes::serverIdOf)
        when (val directive = serverSwitchDirective(current, serverId)) {
            ServerSwitchDirective.None -> Unit
            is ServerSwitchDirective.SwitchTo -> navController.navigate(
                ArrRoutes.encode(ArrRoute.ServerHome(serverId)),
            ) {
                launchSingleTop = true
                popUpTo(ArrRoutes.SERVER_LIST_ROUTE) {
                    inclusive = false
                    saveState = directive.saveState
                }
                restoreState = directive.restoreState
            }
        }
    }

    override fun back(): Boolean = navController.popBackStack()
}

/** Rebuilds the typed route of a live entry from its destination pattern and args. */
private fun NavBackStackEntry.toArrRoute(registry: ArrRouteRegistry): ArrRoute? {
    val pattern = destination.route ?: return null
    return when (pattern) {
        ArrRoutes.SERVER_LIST_ROUTE -> ArrRoute.ServerList
        ArrRoutes.SERVER_HOME_PATTERN -> serverIdArg()?.let { ArrRoute.ServerHome(it) }
        ArrRoutes.LIBRARY_PATTERN -> appRouteArgs()?.let { (id, app) -> ArrRoute.Library(id, app) }
        ArrRoutes.DETAIL_PATTERN -> detailArgs()?.let { (id, app, entityId) ->
            ArrRoute.Detail(id, app, entityId)
        }
        ArrRoutes.ADD_PATTERN -> appRouteArgs()?.let { (id, app) -> ArrRoute.Add(id, app) }
        ArrRoutes.QUEUE_PATTERN -> appRouteArgs()?.let { (id, app) -> ArrRoute.Queue(id, app) }
        ArrRoutes.CALENDAR_PATTERN -> appRouteArgs()?.let { (id, app) ->
            ArrRoute.Calendar(id, app)
        }
        ArrRoutes.SETTINGS_PATTERN -> appRouteArgs()?.let { (id, app) ->
            ArrRoute.Settings(id, app)
        }
        else -> {
            val nested = registry.nestedRoutes()
                .firstOrNull { nestedNavPattern(it) == pattern } ?: return null
            featureArgs(nested)
        }
    }
}

private fun NavBackStackEntry.arg(name: String): String? = arguments?.getString(name)?.takeIf {
    it.isNotEmpty()
}

private fun NavBackStackEntry.serverIdArg(): ServerId? =
    arg("serverId")?.let(::ServerId)

private fun NavBackStackEntry.appArg(): AppType? =
    arg("app")?.let { ArrRoutes.appFromWireName(it) }

private fun NavBackStackEntry.appRouteArgs(): Pair<ServerId, AppType>? {
    val serverId = serverIdArg() ?: return null
    val app = appArg() ?: return null
    return serverId to app
}

private fun NavBackStackEntry.detailArgs(): Triple<ServerId, AppType, String>? {
    val (serverId, app) = appRouteArgs() ?: return null
    val entityId = arg("entityId") ?: return null
    return Triple(serverId, app, entityId)
}

private fun NavBackStackEntry.featureArgs(nested: ArrNestedRoute): ArrRoute.Feature? {
    val values = patternParams(nested.pattern).associateWith { arg(it) ?: return null }
    val path = fillPattern(nested.pattern, values) ?: return null
    return ArrRoute.Feature(nested.owner, path)
}
