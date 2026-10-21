package dev.appolon.homelab.arr.feature.radarr.wanted

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.radarr.wanted.data.RadarrWantedRepository
import dev.appolon.homelab.arr.feature.radarr.wanted.data.WantedCounts
import dev.appolon.homelab.arr.feature.radarr.wanted.data.WantedTab
import dev.appolon.homelab.arr.feature.radarr.wanted.ui.RadarrWantedScreen
import dev.appolon.homelab.arr.feature.radarr.wanted.vm.WantedViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Shell integration for the Radarr wanted feature: the nested routes this
 * area owns, open helpers, and the shell feature slot. The integration layer
 * calls [register] once before composing the shell and passes
 * [radarrWantedShellContent] into it.
 */
object RadarrWantedFeature {
    const val ROUTE_OWNER = "radarr-wanted"

    /** Wanted destination for one server, opening on the missing tab. */
    const val WANTED_PATTERN = "wanted/{serverId}"

    /** Same screen opening directly on one tab (`missing` or `cutoff`). */
    const val WANTED_TAB_PATTERN = "wanted/{serverId}/tab/{tab}"

    val PATTERNS = listOf(WANTED_PATTERN, WANTED_TAB_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
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
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }
}

/**
 * Shell content for this area: the feature slot handling this module's nested
 * routes. Foreign feature owners render nothing so a composing integration
 * can chain several feature slots in one shell content.
 */
fun radarrWantedShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    feature = { route -> RadarrWantedFeatureSlot(route, graph) },
)

/**
 * The wanted screen of one Radarr server, wired to the [ArrDataGraph]
 * singletons. The connection resolves asynchronously (registry read plus key
 * decryption); a spinner covers that gap. [onCountsChanged] is the shell's
 * badge hook: it fires with the server-reported totals whenever they move.
 */
@Composable
fun RadarrWantedDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
    initialTab: WantedTab = WantedTab.MISSING,
    onCountsChanged: ((WantedCounts) -> Unit)? = null,
) {
    val connection = remember(serverId) {
        RadarrWantedConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<RadarrWantedApis?>(null) }
    LaunchedEffect(serverId) {
        connected = connection.connect(serverId)
    }
    val apis = connected
    if (apis == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val vm: WantedViewModel = viewModel(key = "radarr-wanted-" + serverId.value) {
        WantedViewModel(
            repository = RadarrWantedRepository(
                serverId = serverId,
                store = graph.entityStore,
                missingApi = apis.missingApi,
                cutoffApi = apis.cutoffApi,
                qualityProfileApi = apis.qualityProfiles,
                commandRunner = apis.commandRunner,
                actionQueue = graph.actionQueue,
            ),
        )
    }
    LaunchedEffect(initialTab) {
        vm.selectTab(initialTab)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()
    val counts by vm.counts.collectAsState()
    LaunchedEffect(counts) {
        onCountsChanged?.invoke(counts)
    }
    val navigator = LocalArrShellNavigator.current
    val context = LocalContext.current
    val imageLoader = remember(apis) { apis.imageLoader(context) }

    RadarrWantedScreen(
        state = state,
        lce = lce,
        counts = counts,
        baseUrl = apis.baseUrl,
        imageLoader = imageLoader,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
        onTabSelect = vm::selectTab,
        onToggleMonitored = vm::toggleMonitored,
        onQualityToggle = vm::toggleQuality,
        onClearFilters = vm::clearFilters,
        onMovieOpen = remember(navigator, serverId) { { movieId: Int ->
            navigator.open(ArrRoute.Detail(serverId, AppType.RADARR, movieId.toString()))
        } },
        onSearchMovie = vm::searchMovie,
        onSearchAll = vm::searchAll,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}

/**
 * Feature-slot entry: renders this module's nested routes and defers to the
 * caller for foreign owners by rendering nothing, so the integration layer
 * can stack several feature slots in one shell content.
 */
@Composable
fun RadarrWantedFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != RadarrWantedFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val withTab = matchRoutePattern(RadarrWantedFeature.WANTED_TAB_PATTERN, segments)
    val plain = matchRoutePattern(RadarrWantedFeature.WANTED_PATTERN, segments)
    val tab = withTab?.get("tab")
        ?.let { wireName -> WantedTab.entries.firstOrNull { it.wireName == wireName } }
    when {
        withTab != null -> RadarrWantedDestination(
            serverId = ServerId(withTab.getValue("serverId")),
            graph = graph,
            modifier = modifier,
            initialTab = tab ?: WantedTab.MISSING,
        )

        plain != null -> RadarrWantedDestination(
            serverId = ServerId(plain.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )
    }
}
