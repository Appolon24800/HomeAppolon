package dev.appolon.homelab.arr.feature.bazarr.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.bazarr.history.data.BazarrHistoryRepository
import dev.appolon.homelab.arr.feature.bazarr.history.ui.BazarrBlacklistScreen
import dev.appolon.homelab.arr.feature.bazarr.history.ui.BazarrHistoryScreen
import dev.appolon.homelab.arr.feature.bazarr.history.vm.BazarrBlacklistViewModel
import dev.appolon.homelab.arr.feature.bazarr.history.vm.BazarrHistoryViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Shell integration for the Bazarr history and blacklist screens: the
 * nested routes this group owns, open helpers for sibling groups, and the
 * feature slot composable. The integration layer calls [register] once
 * before composing the shell.
 */
object BazarrHistoryFeature {
    const val ROUTE_OWNER = "bazarr-history"

    /** History of subtitle decisions, paged and filterable. */
    const val HISTORY_PATTERN = "history/{serverId}"

    /** Blacklisted subtitle providers for episodes and movies. */
    const val BLACKLIST_PATTERN = "blacklist/{serverId}"

    val PATTERNS = listOf(HISTORY_PATTERN, BLACKLIST_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openHistory(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(HISTORY_PATTERN, mapOf("serverId" to serverId.value))
        path?.let { navigator.open(ArrRoute.Feature(ROUTE_OWNER, it)) }
    }

    fun openBlacklist(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(BLACKLIST_PATTERN, mapOf("serverId" to serverId.value))
        path?.let { navigator.open(ArrRoute.Feature(ROUTE_OWNER, it)) }
    }
}

/**
 * Feature-slot entry: renders this group's nested routes and defers to the
 * previous slot for foreign owners, so an integration layer can stack
 * several feature slots in one shell content.
 */
fun ArrShellContent.withBazarrHistory(graph: ArrDataGraph): ArrShellContent {
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
            if (route.owner == BazarrHistoryFeature.ROUTE_OWNER) {
                BazarrHistoryFeatureSlot(route, graph)
            } else {
                previousFeature?.invoke(route)
            }
        },
    )
}

/** Dispatches one feature route of this owner to its destination composable. */
@Composable
fun BazarrHistoryFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val segments = route.path.split('/')
    val history = matchRoutePattern(BazarrHistoryFeature.HISTORY_PATTERN, segments)
    val blacklist = matchRoutePattern(BazarrHistoryFeature.BLACKLIST_PATTERN, segments)
    when {
        history != null -> BazarrHistoryDestination(
            serverId = ServerId(history.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )

        blacklist != null -> BazarrBlacklistDestination(
            serverId = ServerId(blacklist.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )
    }
}

/**
 * Connects to the server, builds the repository over the entity store and
 * action queue, then renders the subtitle history screen. The connection
 * gate keeps the ViewModel constructor synchronous and testable.
 */
@Composable
fun BazarrHistoryDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    var connected by remember(serverId) { mutableStateOf<BazarrHistoryApis?>(null) }
    LaunchedEffect(serverId) {
        connected = BazarrHistoryConnection(graph.registry, graph.secrets).connect(serverId)
    }
    val apis = connected
    if (apis == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val repository = remember(serverId, apis) {
        BazarrHistoryRepository(
            serverId = serverId,
            store = graph.entityStore,
            episodesHistoryApi = apis.episodesHistoryApi,
            moviesHistoryApi = apis.moviesHistoryApi,
            episodesBlacklistApi = apis.episodesBlacklistApi,
            moviesBlacklistApi = apis.moviesBlacklistApi,
            languagesApi = apis.languagesApi,
            actionQueue = graph.actionQueue,
        )
    }
    val vm: BazarrHistoryViewModel = viewModel(
        key = "bazarr-history-" + serverId.value,
        factory = viewModelFactory { initializer { BazarrHistoryViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    BazarrHistoryScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
        onLanguageFilter = vm::setLanguageFilter,
        onMediaFilter = vm::setMediaFilter,
        onOpenBlacklist = { BazarrHistoryFeature.openBlacklist(navigator, serverId) },
        modifier = modifier,
    )
}

/** Blacklist twin of [BazarrHistoryDestination]. */
@Composable
fun BazarrBlacklistDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    var connected by remember(serverId) { mutableStateOf<BazarrHistoryApis?>(null) }
    LaunchedEffect(serverId) {
        connected = BazarrHistoryConnection(graph.registry, graph.secrets).connect(serverId)
    }
    val apis = connected
    if (apis == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val repository = remember(serverId, apis) {
        BazarrHistoryRepository(
            serverId = serverId,
            store = graph.entityStore,
            episodesHistoryApi = apis.episodesHistoryApi,
            moviesHistoryApi = apis.moviesHistoryApi,
            episodesBlacklistApi = apis.episodesBlacklistApi,
            moviesBlacklistApi = apis.moviesBlacklistApi,
            languagesApi = apis.languagesApi,
            actionQueue = graph.actionQueue,
        )
    }
    val vm: BazarrBlacklistViewModel = viewModel(
        key = "bazarr-blacklist-" + serverId.value,
        factory = viewModelFactory { initializer { BazarrBlacklistViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    BazarrBlacklistScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        onTab = vm::setTab,
        onRemove = vm::remove,
        onClearVisible = vm::clearVisible,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}
