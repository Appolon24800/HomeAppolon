package dev.appolon.homelab.arr.feature.bazarr.wanted

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
import dev.appolon.homelab.arr.feature.bazarr.episodes.BazarrEpisodesFeature
import dev.appolon.homelab.arr.feature.bazarr.wanted.data.BazarrWantedRepository
import dev.appolon.homelab.arr.feature.bazarr.wanted.ui.BazarrWantedScreen
import dev.appolon.homelab.arr.feature.bazarr.wanted.vm.BazarrWantedViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Shell integration for the Bazarr wanted subtitles screens: the nested
 * routes this group owns, open helpers for sibling groups (the series list
 * navigates here), and the feature slot composable. The integration layer
 * calls [register] once before composing the shell.
 */
object BazarrWantedFeature {
    const val ROUTE_OWNER = "bazarr-wanted"

    /** Wanted (missing) subtitles across episodes and movies, with bulk search. */
    const val WANTED_PATTERN = "wanted/{serverId}"

    val PATTERNS = listOf(WANTED_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openWanted(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(WANTED_PATTERN, mapOf("serverId" to serverId.value))
        path?.let { navigator.open(ArrRoute.Feature(ROUTE_OWNER, it)) }
    }
}

/**
 * Feature-slot entry: renders this group's nested routes and defers to the
 * previous slot for foreign owners, so an integration layer can stack
 * several feature slots in one shell content.
 */
fun ArrShellContent.withBazarrWanted(graph: ArrDataGraph): ArrShellContent {
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
            if (route.owner == BazarrWantedFeature.ROUTE_OWNER) {
                BazarrWantedFeatureSlot(route, graph)
            } else {
                previousFeature?.invoke(route)
            }
        },
    )
}

/** Dispatches one feature route of this owner to its destination composable. */
@Composable
fun BazarrWantedFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val segments = route.path.split('/')
    val wanted = matchRoutePattern(BazarrWantedFeature.WANTED_PATTERN, segments)
    if (wanted != null) {
        BazarrWantedDestination(
            serverId = ServerId(wanted.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )
    }
}

/**
 * Connects to the server, builds the repository over the entity store and
 * action queue, then renders the wanted subtitles screen. The connection
 * gate keeps the ViewModel constructor synchronous and testable.
 */
@Composable
fun BazarrWantedDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    var connected by remember(serverId) { mutableStateOf<BazarrWantedApis?>(null) }
    LaunchedEffect(serverId) {
        connected = BazarrWantedConnection(graph.registry, graph.secrets).connect(serverId)
    }
    val apis = connected
    if (apis == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val repository = remember(serverId, apis) {
        BazarrWantedRepository(
            serverId = serverId,
            store = graph.entityStore,
            episodesWantedApi = apis.episodesWantedApi,
            moviesWantedApi = apis.moviesWantedApi,
            episodesSubtitlesApi = apis.episodesSubtitlesApi,
            moviesSubtitlesApi = apis.moviesSubtitlesApi,
            badgesApi = apis.badgesApi,
            languagesApi = apis.languagesApi,
            actionQueue = graph.actionQueue,
        )
    }
    val vm: BazarrWantedViewModel = viewModel(
        key = "bazarr-wanted-" + serverId.value,
        factory = viewModelFactory { initializer { BazarrWantedViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    BazarrWantedScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        onTab = vm::setTab,
        onLanguageFilter = vm::setLanguageFilter,
        onSearchEpisode = vm::searchEpisode,
        onSearchMovie = vm::searchMovie,
        onToggleEpisodeSelected = vm::toggleEpisodeSelected,
        onToggleMovieSelected = vm::toggleMovieSelected,
        onSelectAll = vm::selectAllPresented,
        onClearSelection = vm::clearSelection,
        onSearchSelected = vm::searchSelected,
        onSearchAll = vm::searchAllPresented,
        onOpenEpisodeSeries = { row ->
            BazarrEpisodesFeature.openSeries(navigator, serverId, row.seriesId)
        },
        onOpenMovie = { row ->
            BazarrEpisodesFeature.openMovie(navigator, serverId, row.radarrId)
        },
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}
