package dev.appolon.homelab.arr.feature.bazarr.series

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLibraryRepository
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMediaKind
import dev.appolon.homelab.arr.feature.bazarr.series.ui.BazarrEpisodesScreen
import dev.appolon.homelab.arr.feature.bazarr.series.ui.BazarrMoviesListScreen
import dev.appolon.homelab.arr.feature.bazarr.series.ui.BazarrSeriesListScreen
import dev.appolon.homelab.arr.feature.bazarr.series.vm.BazarrEpisodesViewModel
import dev.appolon.homelab.arr.feature.bazarr.series.vm.BazarrLibraryViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Shell integration for the Bazarr series and movies lists: the nested routes
 * this module owns, open helpers, and the shell slot composables. The
 * integration layer calls [BazarrLibraryFeature.register] once before
 * composing the shell and passes [bazarrLibraryShellContent] into it.
 *
 * Episode and movie subtitle detail live in the later subtitles feature, so
 * this module hands off through [BazarrNavSlots] callbacks instead of owning
 * those routes; the defaults are no-ops until that feature wires them.
 */
object BazarrLibraryFeature {
    const val ROUTE_OWNER = "bazarr-library"

    /** Series list of one server, server id embedded in the path. */
    const val SERIES_PATTERN = "series/{serverId}"

    /** Movies list of one server. */
    const val MOVIES_PATTERN = "movies/{serverId}"

    /** Episodes browser of one series. */
    const val EPISODES_PATTERN = "series/{serverId}/{seriesId}/episodes"

    val PATTERNS = listOf(SERIES_PATTERN, MOVIES_PATTERN, EPISODES_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openSeries(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(SERIES_PATTERN, mapOf("serverId" to serverId.value)) ?: SERIES_PATTERN
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }

    fun openMovies(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(MOVIES_PATTERN, mapOf("serverId" to serverId.value)) ?: MOVIES_PATTERN
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }

    fun openEpisodes(navigator: ArrShellNavigator, serverId: ServerId, seriesId: Int) {
        val path = fillPattern(
            EPISODES_PATTERN,
            mapOf("serverId" to serverId.value, "seriesId" to seriesId.toString()),
        ) ?: return
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }
}

/**
 * Callback slots for destinations owned by the subtitles feature: the episode
 * and movie subtitle detail screens. Defaults are no-ops; the integration
 * layer replaces them when that feature lands.
 */
data class BazarrNavSlots(
    val onOpenEpisodeSubtitles: (serverId: ServerId, seriesId: Int, episodeId: Int) -> Unit =
        { _, _, _ -> },
    val onOpenMovieSubtitles: (serverId: ServerId, radarrId: Int) -> Unit = { _, _ -> },
)

/**
 * Shell content for this module: the library slot of `ArrRoute.Library` for
 * Bazarr and the feature slot handling this module's nested routes. Foreign
 * feature owners fall through so a composing integration can chain slots.
 */
fun bazarrLibraryShellContent(
    graph: ArrDataGraph,
    navSlots: BazarrNavSlots = BazarrNavSlots(),
): ArrShellContent = ArrShellContent(
    library = { route ->
        if (route.app == AppType.BAZARR) {
            BazarrLibraryDestination(route.serverId, BazarrMediaKind.SERIES, graph, navSlots)
        }
    },
    feature = { route -> BazarrLibraryFeatureSlot(route, graph, navSlots) },
)

/**
 * The Bazarr library, wired to the [ArrDataGraph] singletons: a tab row over
 * the series and movies lists. The connection resolves asynchronously
 * (registry read plus key decryption); a spinner covers that gap, then the
 * ViewModels build on the connected API bundle.
 */
@Composable
fun BazarrLibraryDestination(
    serverId: ServerId,
    initialKind: BazarrMediaKind,
    graph: ArrDataGraph,
    navSlots: BazarrNavSlots,
    modifier: Modifier = Modifier,
) {
    val connection = remember(serverId) {
        BazarrLibraryConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<BazarrLibraryApiBundle?>(null) }
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
    val context = LocalContext.current
    val imageLoader = remember(apis) { apis.imageLoader(context) }
    var kind by remember(serverId) { mutableStateOf(initialKind) }
    val navigator = LocalArrShellNavigator.current

    if (kind == BazarrMediaKind.SERIES) {
        val vm: BazarrLibraryViewModel = viewModel(
            key = "bazarr-library-series-" + serverId.value,
        ) {
            BazarrLibraryViewModel(bazarrRepository(apis, serverId, graph), BazarrMediaKind.SERIES)
        }
        val state by vm.state.collectAsState()
        val lce by vm.lce.collectAsState()
        BazarrSeriesListScreen(
            state = state,
            lce = lce,
            imageLoader = imageLoader,
            tabs = { LibraryTabs(BazarrMediaKind.SERIES) { kind = it } },
            onRefresh = vm::refresh,
            onLoadMore = vm::loadMore,
            onViewChange = vm::setView,
            onSortSelect = vm::setSort,
            onToggleSortDescending = vm::toggleSortDescending,
            onFilterValueChange = vm::setFilterValue,
            onClearFilters = vm::clearFilters,
            onSeriesActivate = { seriesId ->
                if (state.selectMode) {
                    vm.toggleSelected(seriesId)
                } else {
                    BazarrLibraryFeature.openEpisodes(navigator, serverId, seriesId)
                }
            },
            onSeriesLongClick = vm::enterSelectMode,
            onSelectAll = vm::selectAllPresented,
            onExitSelection = vm::exitSelectMode,
            onApplyProfile = vm::applyProfile,
            onConsumeMessage = vm::consumeMessage,
            modifier = modifier,
        )
    } else {
        val vm: BazarrLibraryViewModel = viewModel(
            key = "bazarr-library-movies-" + serverId.value,
        ) {
            BazarrLibraryViewModel(bazarrRepository(apis, serverId, graph), BazarrMediaKind.MOVIES)
        }
        val state by vm.state.collectAsState()
        val lce by vm.lce.collectAsState()
        BazarrMoviesListScreen(
            state = state,
            lce = lce,
            imageLoader = imageLoader,
            tabs = { LibraryTabs(BazarrMediaKind.MOVIES) { kind = it } },
            onRefresh = vm::refresh,
            onLoadMore = vm::loadMore,
            onViewChange = vm::setView,
            onSortSelect = vm::setSort,
            onToggleSortDescending = vm::toggleSortDescending,
            onFilterValueChange = vm::setFilterValue,
            onClearFilters = vm::clearFilters,
            onMovieActivate = { radarrId ->
                if (state.selectMode) {
                    vm.toggleSelected(radarrId)
                } else {
                    navSlots.onOpenMovieSubtitles(serverId, radarrId)
                }
            },
            onMovieLongClick = vm::enterSelectMode,
            onSelectAll = vm::selectAllPresented,
            onExitSelection = vm::exitSelectMode,
            onApplyProfile = vm::applyProfile,
            onConsumeMessage = vm::consumeMessage,
            modifier = modifier,
        )
    }
}

/** One repository over the shared entity store, action queue, and API bundle. */
private fun bazarrRepository(
    apis: BazarrLibraryApiBundle,
    serverId: ServerId,
    graph: ArrDataGraph,
): BazarrLibraryRepository = BazarrLibraryRepository(
    serverId = serverId,
    store = graph.entityStore,
    seriesApi = apis.seriesApi,
    moviesApi = apis.moviesApi,
    episodesApi = apis.episodesApi,
    badgesApi = apis.badgesApi,
    episodesBlacklistApi = apis.episodesBlacklistApi,
    moviesBlacklistApi = apis.moviesBlacklistApi,
    languageProfilesApi = apis.languageProfilesApi,
    actionQueue = graph.actionQueue,
)

/** The series/movies tab row shared by both list screens. */
@Composable
private fun LibraryTabs(selected: BazarrMediaKind, onSelect: (BazarrMediaKind) -> Unit) {
    TabRow(selectedTabIndex = if (selected == BazarrMediaKind.SERIES) 0 else 1) {
        Tab(
            selected = selected == BazarrMediaKind.SERIES,
            onClick = { onSelect(BazarrMediaKind.SERIES) },
            text = { Text(stringResource(R.string.arr_bazarr_lib_tab_series)) },
        )
        Tab(
            selected = selected == BazarrMediaKind.MOVIES,
            onClick = { onSelect(BazarrMediaKind.MOVIES) },
            text = { Text(stringResource(R.string.arr_bazarr_lib_tab_movies)) },
        )
    }
}

/**
 * The episodes browser of one series, wired to the [ArrDataGraph] singletons.
 * Activating an episode hands off to the subtitles feature through
 * [BazarrNavSlots.onOpenEpisodeSubtitles].
 */
@Composable
fun BazarrEpisodesDestination(
    serverId: ServerId,
    seriesId: Int,
    graph: ArrDataGraph,
    navSlots: BazarrNavSlots,
    modifier: Modifier = Modifier,
) {
    val connection = remember(serverId) {
        BazarrLibraryConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<BazarrLibraryApiBundle?>(null) }
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
    val vm: BazarrEpisodesViewModel = viewModel(
        key = "bazarr-episodes-$seriesId-" + serverId.value,
    ) {
        BazarrEpisodesViewModel(bazarrRepository(apis, serverId, graph), seriesId)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()
    val navigator = LocalArrShellNavigator.current
    BazarrEpisodesScreen(
        state = state,
        lce = lce,
        onRefresh = vm::refresh,
        onBack = { navigator.back() },
        onEpisodeActivate = { _, episodeId ->
            navSlots.onOpenEpisodeSubtitles(serverId, seriesId, episodeId)
        },
        modifier = modifier,
    )
}

/**
 * Feature-slot entry: renders this module's nested routes and defers to the
 * caller for foreign owners by rendering nothing, so the integration layer
 * can stack several feature slots in one shell content.
 */
@Composable
fun BazarrLibraryFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    navSlots: BazarrNavSlots = BazarrNavSlots(),
    modifier: Modifier = Modifier,
) {
    if (route.owner != BazarrLibraryFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val episodes = matchRoutePattern(BazarrLibraryFeature.EPISODES_PATTERN, segments)
    val series = matchRoutePattern(BazarrLibraryFeature.SERIES_PATTERN, segments)
    val movies = matchRoutePattern(BazarrLibraryFeature.MOVIES_PATTERN, segments)
    when {
        episodes != null -> BazarrEpisodesDestination(
            serverId = ServerId(episodes.getValue("serverId")),
            seriesId = episodes.getValue("seriesId").toIntOrNull() ?: return,
            graph = graph,
            navSlots = navSlots,
            modifier = modifier,
        )

        series != null -> BazarrLibraryDestination(
            serverId = ServerId(series.getValue("serverId")),
            initialKind = BazarrMediaKind.SERIES,
            graph = graph,
            navSlots = navSlots,
            modifier = modifier,
        )

        movies != null -> BazarrLibraryDestination(
            serverId = ServerId(movies.getValue("serverId")),
            initialKind = BazarrMediaKind.MOVIES,
            graph = graph,
            navSlots = navSlots,
            modifier = modifier,
        )
    }
}
