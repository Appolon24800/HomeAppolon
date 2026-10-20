package dev.appolon.homelab.arr.feature.bazarr.episodes

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
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.BazarrSubtitlesRepository
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.EpisodeSubtitlesScreen
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.MovieSubtitlesScreen
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.SubtitleHistoryScreen
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.WantedSubtitlesScreen
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.EpisodeSubtitlesViewModel
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.MovieSubtitlesViewModel
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.SubtitleHistoryViewModel
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.WantedSubtitlesViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Shell integration for the Bazarr episodes and movie subtitle screens: the
 * nested routes this group owns, open helpers for sibling groups (the series
 * list navigates here), and the feature slot composables. The integration
 * layer calls [register] once before composing the shell.
 */
object BazarrEpisodesFeature {
    const val ROUTE_OWNER = "bazarr-episodes"

    /** Episode subtitle list for one series; series id in the path. */
    const val SERIES_PATTERN = "series/{serverId}/{seriesId}"

    /** Movie subtitle list for one Radarr movie. */
    const val MOVIE_PATTERN = "movie/{serverId}/{radarrId}"

    /** Wanted (missing) subtitles across episodes and movies. */
    const val WANTED_PATTERN = "wanted/{serverId}"

    /** History of subtitle decisions. */
    const val HISTORY_PATTERN = "history/{serverId}"

    val PATTERNS = listOf(SERIES_PATTERN, MOVIE_PATTERN, WANTED_PATTERN, HISTORY_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openSeries(navigator: ArrShellNavigator, serverId: ServerId, seriesId: Int) {
        val path = fillPattern(SERIES_PATTERN, mapOf("serverId" to serverId.value, "seriesId" to seriesId.toString()))
        path?.let { navigator.open(ArrRoute.Feature(ROUTE_OWNER, it)) }
    }

    fun openMovie(navigator: ArrShellNavigator, serverId: ServerId, radarrId: Int) {
        val path = fillPattern(MOVIE_PATTERN, mapOf("serverId" to serverId.value, "radarrId" to radarrId.toString()))
        path?.let { navigator.open(ArrRoute.Feature(ROUTE_OWNER, it)) }
    }

    fun openWanted(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(WANTED_PATTERN, mapOf("serverId" to serverId.value))
        path?.let { navigator.open(ArrRoute.Feature(ROUTE_OWNER, it)) }
    }

    fun openHistory(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(HISTORY_PATTERN, mapOf("serverId" to serverId.value))
        path?.let { navigator.open(ArrRoute.Feature(ROUTE_OWNER, it)) }
    }
}

/**
 * Feature-slot entry: renders this group's nested routes and defers to the
 * previous slot for foreign owners, so an integration layer can stack
 * several feature slots in one shell content. The series list group, for
 * example, can wrap its own slot around the result of this call.
 */
fun ArrShellContent.withBazarrEpisodes(graph: ArrDataGraph): ArrShellContent {
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
            if (route.owner == BazarrEpisodesFeature.ROUTE_OWNER) {
                BazarrEpisodesFeatureSlot(route, graph)
            } else {
                previousFeature?.invoke(route)
            }
        },
    )
}

/** Dispatches one feature route of this owner to its destination composable. */
@Composable
fun BazarrEpisodesFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val segments = route.path.split('/')
    val series = matchRoutePattern(BazarrEpisodesFeature.SERIES_PATTERN, segments)
    val movie = matchRoutePattern(BazarrEpisodesFeature.MOVIE_PATTERN, segments)
    val wanted = matchRoutePattern(BazarrEpisodesFeature.WANTED_PATTERN, segments)
    val history = matchRoutePattern(BazarrEpisodesFeature.HISTORY_PATTERN, segments)
    when {
        series != null -> EpisodeSubtitlesDestination(
            serverId = ServerId(series.getValue("serverId")),
            seriesId = series.getValue("seriesId").toIntOrNull() ?: return,
            graph = graph,
            modifier = modifier,
        )

        movie != null -> MovieSubtitlesDestination(
            serverId = ServerId(movie.getValue("serverId")),
            radarrId = movie.getValue("radarrId").toIntOrNull() ?: return,
            graph = graph,
            modifier = modifier,
        )

        wanted != null -> WantedSubtitlesDestination(
            serverId = ServerId(wanted.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )

        history != null -> SubtitleHistoryDestination(
            serverId = ServerId(history.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )
    }
}

/**
 * Connects to the server, builds the repository over the entity store and
 * action queue, then renders the episode subtitle list. The connection gate
 * keeps the ViewModel constructor synchronous and testable.
 */
@Composable
fun EpisodeSubtitlesDestination(
    serverId: ServerId,
    seriesId: Int,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    var connected by remember(serverId) { mutableStateOf<BazarrEpisodesApis?>(null) }
    LaunchedEffect(serverId) {
        connected = BazarrEpisodesConnection(graph.registry, graph.secrets).connect(serverId)
    }
    val apis = connected
    if (apis == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val repository = remember(serverId, seriesId, apis) {
        apis.repository(serverId, graph)
    }
    val vm: EpisodeSubtitlesViewModel = viewModel(
        key = "bazarr-episodes-$seriesId-" + serverId.value,
        factory = viewModelFactory {
            initializer { EpisodeSubtitlesViewModel(seriesId, repository) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    EpisodeSubtitlesScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        onLanguageFilter = vm::setLanguageFilter,
        onToggleMissingOnly = vm::toggleMissingOnly,
        onDownload = vm::downloadSubtitle,
        onDelete = vm::deleteSubtitle,
        onOpenManualSearch = vm::openManualSearch,
        onDownloadManual = vm::downloadManualResult,
        onRetryManualSearch = vm::retryManualSearch,
        onCloseManualSearch = vm::closeManualSearch,
        onOpenViewer = vm::openViewer,
        onCloseViewer = vm::closeViewer,
        onOpenUpload = vm::openUpload,
        onUpdateUpload = { form -> vm.updateUpload { form } },
        onSubmitUpload = vm::submitUpload,
        onCloseUpload = vm::closeUpload,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}

/** Movie twin of [EpisodeSubtitlesDestination]. */
@Composable
fun MovieSubtitlesDestination(
    serverId: ServerId,
    radarrId: Int,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    var connected by remember(serverId) { mutableStateOf<BazarrEpisodesApis?>(null) }
    LaunchedEffect(serverId) {
        connected = BazarrEpisodesConnection(graph.registry, graph.secrets).connect(serverId)
    }
    val apis = connected
    if (apis == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val repository = remember(serverId, radarrId, apis) { apis.repository(serverId, graph) }
    val vm: MovieSubtitlesViewModel = viewModel(
        key = "bazarr-movie-$radarrId-" + serverId.value,
        factory = viewModelFactory {
            initializer { MovieSubtitlesViewModel(radarrId, repository) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    MovieSubtitlesScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        onDownload = vm::downloadSubtitle,
        onDelete = vm::deleteSubtitle,
        onOpenManualSearch = vm::openManualSearch,
        onDownloadManual = vm::downloadManualResult,
        onRetryManualSearch = vm::retryManualSearch,
        onCloseManualSearch = vm::closeManualSearch,
        onOpenViewer = vm::openViewer,
        onCloseViewer = vm::closeViewer,
        onOpenUpload = vm::openUpload,
        onUpdateUpload = { form -> vm.updateUpload { form } },
        onSubmitUpload = vm::submitUpload,
        onCloseUpload = vm::closeUpload,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}

/** Wanted subtitles destination. */
@Composable
fun WantedSubtitlesDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    var connected by remember(serverId) { mutableStateOf<BazarrEpisodesApis?>(null) }
    LaunchedEffect(serverId) {
        connected = BazarrEpisodesConnection(graph.registry, graph.secrets).connect(serverId)
    }
    val apis = connected
    if (apis == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val repository = remember(serverId, apis) { apis.repository(serverId, graph) }
    val vm: WantedSubtitlesViewModel = viewModel(
        key = "bazarr-wanted-" + serverId.value,
        factory = viewModelFactory { initializer { WantedSubtitlesViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    WantedSubtitlesScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        onTab = vm::setTab,
        onLanguageFilter = vm::setLanguageFilter,
        onSearchEpisode = vm::searchEpisode,
        onSearchMovie = vm::searchMovie,
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

/** History destination. */
@Composable
fun SubtitleHistoryDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    var connected by remember(serverId) { mutableStateOf<BazarrEpisodesApis?>(null) }
    LaunchedEffect(serverId) {
        connected = BazarrEpisodesConnection(graph.registry, graph.secrets).connect(serverId)
    }
    val apis = connected
    if (apis == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val repository = remember(serverId, apis) { apis.repository(serverId, graph) }
    val vm: SubtitleHistoryViewModel = viewModel(
        key = "bazarr-history-" + serverId.value,
        factory = viewModelFactory { initializer { SubtitleHistoryViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    SubtitleHistoryScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        onLanguageFilter = vm::setLanguageFilter,
        onActionFilter = vm::setActionFilter,
        modifier = modifier,
    )
}

/** One repository per connection; every screen of this group shares the shape. */
private fun BazarrEpisodesApis.repository(
    serverId: ServerId,
    graph: ArrDataGraph,
): BazarrSubtitlesRepository = BazarrSubtitlesRepository(
    serverId = serverId,
    store = graph.entityStore,
    seriesApi = seriesApi,
    episodesApi = episodesApi,
    episodesSubtitlesApi = episodesSubtitlesApi,
    episodesWantedApi = episodesWantedApi,
    episodesHistoryApi = episodesHistoryApi,
    moviesApi = moviesApi,
    moviesSubtitlesApi = moviesSubtitlesApi,
    moviesWantedApi = moviesWantedApi,
    moviesHistoryApi = moviesHistoryApi,
    providersEpisodesApi = providersEpisodesApi,
    providersMoviesApi = providersMoviesApi,
    subtitleContentsApi = subtitleContentsApi,
    languagesApi = languagesApi,
    actionQueue = graph.actionQueue,
)
