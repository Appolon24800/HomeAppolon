package dev.appolon.homelab.arr.feature.radarr.library

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
import dev.appolon.homelab.arr.feature.radarr.library.data.RadarrMoviesRepository
import dev.appolon.homelab.arr.feature.radarr.library.data.SavedMovieFiltersStore
import dev.appolon.homelab.arr.feature.radarr.library.ui.MovieLibraryScreen
import dev.appolon.homelab.arr.feature.radarr.library.vm.MovieLibraryViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Shell integration for the Radarr movies library: the nested routes this
 * module owns, open helpers, and the shell slot composables. The integration
 * layer calls [register] once before composing the shell and passes
 * [radarrLibraryShellContent] into it. Detail navigation is a callback slot:
 * the default opens the shell's built-in detail route, which renders the
 * shell placeholder until the detail feature lands.
 */
object RadarrLibraryFeature {
    const val ROUTE_OWNER = "radarr-library"

    /** Library destination for one server, server id embedded in the path. */
    const val MOVIES_PATTERN = "movies/{serverId}"

    /** Same library with one saved filter applied on entry. */
    const val MOVIES_FILTER_PATTERN = "movies/{serverId}/filter/{filterId}"

    val PATTERNS = listOf(MOVIES_PATTERN, MOVIES_FILTER_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openMovies(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(MOVIES_PATTERN, mapOf("serverId" to serverId.value))
            ?: MOVIES_PATTERN
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }
}

/**
 * Shell content for this module: the library slot of `ArrRoute.Library` for
 * Radarr and the feature slot handling this module's nested routes. Foreign
 * feature owners fall through so a composing integration can chain slots.
 */
fun radarrLibraryShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    library = { route ->
        if (route.app == AppType.RADARR) {
            RadarrLibraryDestination(route.serverId, graph)
        }
    },
    feature = { route -> RadarrLibraryFeatureSlot(route, graph) },
)

/**
 * The movies library of one Radarr server, wired to the [ArrDataGraph]
 * singletons. The connection resolves asynchronously (registry read plus
 * key decryption); a spinner covers that gap, then the ViewModel builds on
 * the connected API bundle and the authenticated Coil loader.
 */
@Composable
fun RadarrLibraryDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
    initialFilterId: String? = null,
) {
    val connection = remember(serverId) {
        RadarrLibraryConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<RadarrLibraryApis?>(null) }
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
    val vm: MovieLibraryViewModel = viewModel(key = "radarr-library-" + serverId.value) {
        MovieLibraryViewModel(
            repository = RadarrMoviesRepository(
                serverId = serverId,
                store = graph.entityStore,
                movieApi = apis.movieApi,
                movieEditorApi = apis.movieEditorApi,
                qualityProfileApi = apis.qualityProfiles,
                commandRunner = apis.commandRunner,
                actionQueue = graph.actionQueue,
            ),
            savedFilters = SavedMovieFiltersStore(graph.entityStore, serverId),
        )
    }
    LaunchedEffect(initialFilterId) {
        initialFilterId?.let { vm.applySavedFilter(it) }
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()
    val navigator = LocalArrShellNavigator.current
    val onMovieActivate = remember(vm, navigator, serverId, state.selectMode) {
        { movieId: Int ->
            if (state.selectMode) {
                vm.toggleSelected(movieId)
            } else {
                navigator.open(ArrRoute.Detail(serverId, AppType.RADARR, movieId.toString()))
            }
        }
    }

    MovieLibraryScreen(
        state = state,
        lce = lce,
        baseUrl = apis.baseUrl,
        imageLoader = imageLoader,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
        onViewChange = vm::setView,
        onSortSelect = vm::setSort,
        onToggleSortDescending = vm::toggleSortDescending,
        onFilterValueChange = vm::setFilterValue,
        onClearFilters = vm::clearFilters,
        onSaveFilter = vm::saveFilter,
        onApplySavedFilter = vm::applySavedFilter,
        onDeleteSavedFilter = vm::deleteSavedFilter,
        onMovieActivate = onMovieActivate,
        onMovieLongClick = vm::enterSelectMode,
        onSelectAll = vm::selectAllPresented,
        onExitSelection = vm::exitSelectMode,
        onOpenEditor = vm::openEditor,
        onEditorChange = vm::updateEditor,
        onEditorSubmit = vm::submitEditor,
        onEditorDismiss = vm::closeEditor,
        onRequestDelete = vm::requestDelete,
        onDeleteConfirm = vm::confirmDelete,
        onDeleteDismiss = vm::dismissDelete,
        onRefreshMetadata = vm::refreshMetadata,
        onScanDisk = vm::scanDisk,
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
fun RadarrLibraryFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != RadarrLibraryFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val filtered = matchRoutePattern(RadarrLibraryFeature.MOVIES_FILTER_PATTERN, segments)
    val movies = matchRoutePattern(RadarrLibraryFeature.MOVIES_PATTERN, segments)
    when {
        filtered != null -> RadarrLibraryDestination(
            serverId = ServerId(filtered.getValue("serverId")),
            graph = graph,
            modifier = modifier,
            initialFilterId = filtered.getValue("filterId"),
        )

        movies != null -> RadarrLibraryDestination(
            serverId = ServerId(movies.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )
    }
}
