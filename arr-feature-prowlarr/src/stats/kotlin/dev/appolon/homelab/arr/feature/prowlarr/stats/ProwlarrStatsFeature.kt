package dev.appolon.homelab.arr.feature.prowlarr.stats

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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.prowlarr.IndexerStatsApi
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.prowlarr.stats.data.ProwlarrStatsRepository
import dev.appolon.homelab.arr.feature.prowlarr.stats.ui.StatsScreen
import dev.appolon.homelab.arr.feature.prowlarr.stats.vm.StatsViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator

/**
 * Shell integration for the Prowlarr stats feature: the route it owns, the
 * open helper, and the shell slot. The integration layer calls [register]
 * once before composing the shell and merges [prowlarrStatsShellContent]
 * into it.
 */
object ProwlarrStatsFeature {
    const val ROUTE_OWNER = "prowlarr-stats"

    /** Per-indexer statistics with a period selector. */
    const val STATS_PATTERN = "stats/{serverId}"

    val PATTERNS = listOf(STATS_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openStats(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(STATS_PATTERN, mapOf("serverId" to serverId.value))
            ?: STATS_PATTERN
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }
}

/**
 * Shell content for this source set: the feature slot renders the stats
 * screen for this module's route. Foreign owners fall through so
 * integrations can stack several feature slots.
 */
fun prowlarrStatsShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    feature = { route -> ProwlarrStatsFeatureSlot(route, graph) },
)

/** Feature-slot entry rendering this module's route. */
@Composable
fun ProwlarrStatsFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != ProwlarrStatsFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val stats = matchRoutePattern(ProwlarrStatsFeature.STATS_PATTERN, segments)
    if (stats != null) {
        ProwlarrStatsDestination(ServerId(stats.getValue("serverId")), graph, modifier)
    }
}

/**
 * The stats screen of one Prowlarr server. The connection resolves
 * asynchronously; a spinner covers the gap, then the ViewModel builds on the
 * repository.
 */
@Composable
fun ProwlarrStatsDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val connection = remember(serverId) {
        ProwlarrStatsConnection(graph.registry, graph.secrets)
    }
    var api by remember(serverId) { mutableStateOf<IndexerStatsApi?>(null) }
    LaunchedEffect(serverId) {
        api = connection.connect(serverId)
    }
    val connected = api
    if (connected == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val vm: StatsViewModel = viewModel(key = "prowlarr-stats-" + serverId.value) {
        StatsViewModel(
            ProwlarrStatsRepository(
                serverId = serverId,
                store = graph.entityStore,
                statsApi = { start, end ->
                    connected.getIndexerStats(start, end, null, null, null)
                },
            ),
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    StatsScreen(
        state = state,
        lce = lce,
        onRefresh = vm::refresh,
        onPeriodSelect = vm::setPeriod,
        modifier = modifier,
    )
}
