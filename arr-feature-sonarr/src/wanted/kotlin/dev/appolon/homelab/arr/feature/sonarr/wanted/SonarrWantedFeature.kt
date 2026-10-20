package dev.appolon.homelab.arr.feature.sonarr.wanted

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.sonarr.library.SonarrLibraryRoutes
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.SonarrWantedRepository
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.WantedCounts
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.WantedTab
import dev.appolon.homelab.arr.feature.sonarr.wanted.ui.SonarrWantedScreen
import dev.appolon.homelab.arr.feature.sonarr.wanted.vm.WantedViewModel
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Routes this module owns. The wanted screen is feature-routed (the shell has
 * no built-in wanted slot): `wanted/{serverId}` opens on the missing tab and
 * `wanted/{serverId}/tab/{tab}` (`missing` or `cutoff`) lands on one tab, so
 * the shell home and notifications can deep link straight to either list.
 */
object SonarrWantedRoutes {
    const val OWNER = "sonarr-wanted"

    /** Wanted destination for one server, opening on the missing tab. */
    const val WANTED_PATTERN = "wanted/{serverId}"

    /** Same screen opening directly on one tab (`missing` or `cutoff`). */
    const val WANTED_TAB_PATTERN = "wanted/{serverId}/tab/{tab}"

    val PATTERNS = listOf(WANTED_PATTERN, WANTED_TAB_PATTERN)

    /** Registers this module's nested routes; idempotent per pattern. */
    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(OWNER, pattern) }
    }

    fun openWanted(navigator: ArrShellNavigator, serverId: ServerId, tab: WantedTab? = null) {
        val path = if (tab == null) {
            fillPattern(WANTED_PATTERN, mapOf("serverId" to serverId.value))
        } else {
            fillPattern(
                WANTED_TAB_PATTERN,
                mapOf("serverId" to serverId.value, "tab" to tab.wireName),
            )
        } ?: return
        navigator.open(ArrRoute.Feature(OWNER, path))
    }

    /** Decodes one of this module's feature routes; null for other owners or shapes. */
    fun match(route: ArrRoute.Feature): Map<String, String>? {
        if (route.owner != OWNER) return null
        val segments = route.path.split('/')
        return matchRoutePattern(WANTED_TAB_PATTERN, segments)
            ?: matchRoutePattern(WANTED_PATTERN, segments)
    }
}

/** Everything the wanted screen needs from the app's singletons. */
class SonarrWantedDeps(
    val registry: ServerRegistry,
    val secrets: ArrSecrets,
    val entityStore: EntityStore,
    val actionQueue: ActionQueue,
    val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {
    companion object {
        fun from(graph: ArrDataGraph) = SonarrWantedDeps(
            registry = graph.registry,
            secrets = graph.secrets,
            entityStore = graph.entityStore,
            actionQueue = graph.actionQueue,
        )
    }
}

/**
 * Fills the shell's feature slot for this module's nested routes. Other
 * owners and shapes fall through to the previous slot so sibling feature
 * modules can wrap the same content object.
 */
fun ArrShellContent.withSonarrWanted(
    deps: SonarrWantedDeps,
    registry: ArrRouteRegistry,
): ArrShellContent {
    SonarrWantedRoutes.register(registry)
    val previousFeature = feature
    return ArrShellContent(
        serverList = serverList,
        serverHome = serverHome,
        library = library,
        detail = detail,
        add = add,
        queue = queue,
        calendar = calendar,
        settings = settings,
        feature = { route ->
            if (SonarrWantedRoutes.match(route) != null) {
                SonarrWantedFeatureSlot(route, deps)
            } else {
                previousFeature?.invoke(route)
            }
        },
    )
}

/**
 * Stateful entry: connects to the server, builds the repository over the
 * entity store and action queue, then renders the wanted screen. The
 * connection gate keeps the ViewModel constructor synchronous and fully
 * testable. [onCountsChanged] is the shell's badge hook: it fires with the
 * server-reported totals whenever they move. Bulk monitoring hands off to the
 * series editor via [onOpenSeasonPass]; the default routes to the library
 * screen, whose season pass sheet owns the bulk write, so integrations only
 * override it when they host that editor elsewhere.
 */
@Composable
fun SonarrWanted(
    serverId: ServerId,
    deps: SonarrWantedDeps,
    modifier: Modifier = Modifier,
    initialTab: WantedTab = WantedTab.MISSING,
    onCountsChanged: ((WantedCounts) -> Unit)? = null,
    onOpenSeasonPass: (ServerId) -> Unit = {},
) {
    var apis by remember(serverId) { mutableStateOf<SonarrWantedApis?>(null) }
    LaunchedEffect(serverId) {
        apis = SonarrWantedConnection(
            registry = deps.registry,
            secrets = deps.secrets,
            clientFactory = deps.clientFactory,
        ).connect(serverId)
    }
    val connected = apis
    if (connected == null) {
        ArrLceBox(state = LceState.Loading, modifier = modifier.fillMaxSize()) {}
        return
    }
    val repository = remember(serverId, connected) {
        SonarrWantedRepository(
            serverId = serverId,
            store = deps.entityStore,
            missingApi = connected.missingApi,
            cutoffApi = connected.cutoffApi,
            commandRunner = connected.commandRunner,
            actionQueue = deps.actionQueue,
        )
    }
    val vm: WantedViewModel = viewModel(
        key = "sonarr-wanted-" + serverId.value,
    ) {
        WantedViewModel(repository = repository)
    }
    LaunchedEffect(initialTab) {
        vm.selectTab(initialTab)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    LaunchedEffect(counts) {
        onCountsChanged?.invoke(counts)
    }
    val navigator = LocalArrShellNavigator.current
    val context = LocalContext.current
    val imageLoader = remember(connected) { connected.imageLoader(context) }

    SonarrWantedScreen(
        state = state,
        lce = lce,
        counts = counts,
        baseUrl = connected.baseUrl,
        imageLoader = imageLoader,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
        onTabSelect = vm::selectTab,
        onToggleMonitored = vm::toggleMonitored,
        onSeasonToggle = vm::toggleSeason,
        onClearFilters = vm::clearFilters,
        onOpenSeasonPass = {
            val libraryRoute = SonarrLibraryRoutes.libraryRoute(serverId)
            if (libraryRoute != null) {
                navigator.open(libraryRoute)
            } else {
                onOpenSeasonPass(serverId)
            }
        },
        onSeriesOpen = { seriesId ->
            navigator.open(ArrRoute.Detail(serverId, AppType.SONARR, seriesId.toString()))
        },
        onSearchEpisode = vm::searchEpisode,
        onSearchSeries = vm::searchSeries,
        onSearchAll = vm::searchAll,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}

/**
 * Feature-slot entry: renders this module's nested routes. Foreign owners
 * render nothing here so a composing integration can stack several feature
 * slots in one shell content; wire [withSonarrWanted] instead for chaining.
 */
@Composable
fun SonarrWantedFeatureSlot(
    route: ArrRoute.Feature,
    deps: SonarrWantedDeps,
    modifier: Modifier = Modifier,
) {
    val params = SonarrWantedRoutes.match(route) ?: return
    val tab = params["tab"]
        ?.let { wireName -> WantedTab.entries.firstOrNull { it.wireName == wireName } }
    SonarrWanted(
        serverId = ServerId(params.getValue("serverId")),
        deps = deps,
        modifier = modifier,
        initialTab = tab ?: WantedTab.MISSING,
    )
}
