package dev.appolon.homelab.arr.feature.prowlarr.history

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
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.prowlarr.history.data.ProwlarrHistoryRepository
import dev.appolon.homelab.arr.feature.prowlarr.history.ui.HistoryScreen
import dev.appolon.homelab.arr.feature.prowlarr.history.vm.HistoryViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator

/**
 * Shell integration for the Prowlarr history feature: the route it owns, the
 * open helper, and the shell slot. The integration layer calls [register]
 * once before composing the shell and merges [prowlarrHistoryShellContent]
 * into it.
 */
object ProwlarrHistoryFeature {
    const val ROUTE_OWNER = "prowlarr-history"

    /** Query log with filters, infinite scroll, and clear history. */
    const val HISTORY_PATTERN = "history/{serverId}"

    val PATTERNS = listOf(HISTORY_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openHistory(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(HISTORY_PATTERN, mapOf("serverId" to serverId.value))
            ?: HISTORY_PATTERN
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }
}

/**
 * Shell content for this source set: the feature slot renders the history
 * screen for this module's route. Foreign owners fall through so
 * integrations can stack several feature slots.
 */
fun prowlarrHistoryShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    feature = { route -> ProwlarrHistoryFeatureSlot(route, graph) },
)

/** Feature-slot entry rendering this module's route. */
@Composable
fun ProwlarrHistoryFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != ProwlarrHistoryFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val history = matchRoutePattern(ProwlarrHistoryFeature.HISTORY_PATTERN, segments)
    if (history != null) {
        ProwlarrHistoryDestination(ServerId(history.getValue("serverId")), graph, modifier)
    }
}

/**
 * The history screen of one Prowlarr server. The connection resolves
 * asynchronously; a spinner covers the gap, then the ViewModel builds on the
 * repository.
 */
@Composable
fun ProwlarrHistoryDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val connection = remember(serverId) {
        ProwlarrHistoryConnection(graph.registry, graph.secrets)
    }
    var apis by remember(serverId) { mutableStateOf<ProwlarrHistoryFeatureApis?>(null) }
    LaunchedEffect(serverId) {
        apis = connection.connect(serverId)
    }
    val connected = apis
    if (connected == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val vm: HistoryViewModel = viewModel(key = "prowlarr-history-" + serverId.value) {
        HistoryViewModel(
            ProwlarrHistoryRepository(
                serverId = serverId,
                store = graph.entityStore,
                historyApi = { page, pageSize, sortDirection, indexerIds ->
                    connected.historyApi.getHistory(
                        page = page,
                        pageSize = pageSize,
                        sortKey = SORT_KEY_DATE,
                        sortDirection = sortDirection,
                        eventType = null,
                        successful = null,
                        downloadId = null,
                        indexerIds = indexerIds,
                    )
                },
                indexerListApi = connected.indexerApi::getIndexers,
                commandApi = connected.commandApi::createCommand,
                actionQueue = graph.actionQueue,
            ),
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    HistoryScreen(
        state = state,
        lce = lce,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
        onQueryChange = vm::setQuery,
        onIndexerFilterSelect = vm::setIndexerFilter,
        onRequestClear = vm::requestClear,
        onClearConfirm = vm::confirmClear,
        onClearDismiss = vm::dismissClear,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}

private const val SORT_KEY_DATE = "date"
