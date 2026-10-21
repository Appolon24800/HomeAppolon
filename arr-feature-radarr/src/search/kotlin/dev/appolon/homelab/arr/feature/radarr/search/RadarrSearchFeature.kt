package dev.appolon.homelab.arr.feature.radarr.search

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
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrInteractiveImportRepository
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrReleaseRepository
import dev.appolon.homelab.arr.feature.radarr.search.ui.InteractiveImportScreen
import dev.appolon.homelab.arr.feature.radarr.search.ui.RadarrGrabSheet
import dev.appolon.homelab.arr.feature.radarr.search.ui.ReleaseListScreen
import dev.appolon.homelab.arr.feature.radarr.search.vm.GrabOptions
import dev.appolon.homelab.arr.feature.radarr.search.vm.InteractiveImportViewModel
import dev.appolon.homelab.arr.feature.radarr.search.vm.ReleaseListViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Shell integration for the Radarr interactive search group: the nested
 * routes this group owns, open helpers for the other Radarr groups (detail,
 * wanted, calendar) to enter the release list and the interactive import
 * flow, and the shell feature slot rendering them. The integration layer
 * calls [register] once before composing the shell and passes
 * [radarrSearchShellContent] into it.
 */
object RadarrSearchFeature {
    /** Distinct owner so this group's slot chains next to its siblings'. */
    const val ROUTE_OWNER = "radarr-search"

    /** Interactive search results of one movie. */
    const val RELEASES_PATTERN = "releases/{serverId}/{movieId}"

    /** Interactive import decision flow for one movie. */
    const val IMPORT_PATTERN = "import/{serverId}/{movieId}"

    val PATTERNS = listOf(RELEASES_PATTERN, IMPORT_PATTERN)

    /** Idempotent registration of every nested pattern this group owns. */
    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun releasesRoute(serverId: ServerId, movieId: Int): ArrRoute.Feature? {
        val path = fillPattern(
            RELEASES_PATTERN,
            mapOf("serverId" to serverId.value, "movieId" to movieId.toString()),
        ) ?: return null
        return ArrRoute.Feature(ROUTE_OWNER, path)
    }

    /** Opens the interactive search screen; the entry point other groups call. */
    fun openReleases(navigator: ArrShellNavigator, serverId: ServerId, movieId: Int) {
        releasesRoute(serverId, movieId)?.let(navigator::open)
    }

    /** Opens the movie-scoped interactive import decision flow. */
    fun openInteractiveImport(navigator: ArrShellNavigator, serverId: ServerId, movieId: Int) {
        val path = fillPattern(
            IMPORT_PATTERN,
            mapOf("serverId" to serverId.value, "movieId" to movieId.toString()),
        ) ?: return
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }
}

/**
 * Shell content for this group: only the feature slot. Foreign owners render
 * nothing so a composing integration can stack several feature slots.
 */
fun radarrSearchShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    feature = { route -> RadarrSearchFeatureSlot(route, graph) },
)

/**
 * Feature-slot entry: renders this group's nested routes and defers to other
 * slots for foreign owners, mirroring the calendar group's slot shape.
 */
@Composable
fun RadarrSearchFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != RadarrSearchFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val releases = matchRoutePattern(RadarrSearchFeature.RELEASES_PATTERN, segments)
    val import = matchRoutePattern(RadarrSearchFeature.IMPORT_PATTERN, segments)
    when {
        releases != null -> ReleaseListDestination(
            serverId = ServerId(releases.getValue("serverId")),
            movieId = releases.getValue("movieId").toIntOrNull() ?: return,
            graph = graph,
            modifier = modifier,
        )

        import != null -> InteractiveImportDestination(
            serverId = ServerId(import.getValue("serverId")),
            movieId = import.getValue("movieId").toIntOrNull() ?: return,
            graph = graph,
            modifier = modifier,
        )
    }
}

/** Shared per-(graph, server) wiring: connection plus the two repositories. */
private class SearchRepositories(
    private val graph: ArrDataGraph,
    private val serverId: ServerId,
) {
    val connection = RadarrSearchConnection(graph.registry, graph.secrets)

    suspend fun releases(): RadarrReleaseRepository? =
        connection.apis(serverId)?.let { apis ->
            RadarrReleaseRepository(
                serverId = serverId,
                store = graph.entityStore,
                releaseApi = apis.releaseApi,
                releasePushApi = apis.releasePushApi,
                actionQueue = graph.actionQueue,
            )
        }

    suspend fun import(): RadarrInteractiveImportRepository? =
        connection.apis(serverId)?.let { apis ->
            RadarrInteractiveImportRepository(
                serverId = serverId,
                store = graph.entityStore,
                manualImportApi = apis.manualImportApi,
                fileSystemCall = { path, includeFiles ->
                    apis.fileSystemApi.fileSystem(path, includeFiles)
                },
                movieById = { id -> apis.movieApi.get(id).body() },
                languageTable = { apis.languageApi.languages() },
                qualityProfileById = { id -> apis.qualityProfileApi.qualityProfile(id).body() },
                actionQueue = graph.actionQueue,
            )
        }
}

/** Both repositories resolved for one server, ready for the ViewModels. */
private class SearchWiring(
    val releases: RadarrReleaseRepository,
    val import: RadarrInteractiveImportRepository,
)

@Composable
private fun rememberRepositories(
    graph: ArrDataGraph,
    serverId: ServerId,
): SearchRepositories = remember(graph, serverId) { SearchRepositories(graph, serverId) }

/** Interactive search destination: connection spinner, then the screen. */
@Composable
private fun ReleaseListDestination(
    serverId: ServerId,
    movieId: Int,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repositories = rememberRepositories(graph, serverId)
    var wired by remember(serverId) { mutableStateOf<SearchWiring?>(null) }
    LaunchedEffect(serverId, movieId) {
        wired = null
        val releases = repositories.releases() ?: return@LaunchedEffect
        val import = repositories.import() ?: return@LaunchedEffect
        wired = SearchWiring(releases, import)
    }
    val pair = wired
        ?: return Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    val (releaseRepository, importRepository) = pair.let { it.releases to it.import }
    val vm: ReleaseListViewModel = viewModel(
        key = "radarr-releases-" + serverId.value + "-" + movieId,
    ) {
        ReleaseListViewModel(
            movieId = movieId,
            repository = releaseRepository,
            grabOptionsSource = {
                when (val movie = importRepository.movie(movieId)) {
                    is ArrResult.Ok -> GrabOptions(
                        qualities = importRepository.qualityOptions(movie.value),
                        languages = importRepository.languageOptions(),
                    )

                    is ArrResult.Err -> GrabOptions()
                }
            },
        )
    }
    val state by vm.state.collectAsState()
    val queuedMovieIds by vm.queuedMovieIds.collectAsState()
    val navigator = LocalArrShellNavigator.current
    Box(modifier) {
        ReleaseListScreen(
            state = state,
            queued = movieId in queuedMovieIds,
            onRefresh = vm::refresh,
            onRetry = vm::retry,
            onBack = remember(navigator) { { navigator.back() } },
            onSort = vm::setSort,
            onToggleRejections = vm::toggleRejections,
            onGrab = vm::openGrabSheet,
            onConsumeMessage = vm::consumeMessage,
        )
        val target = state.grabTarget
        if (target != null) {
            RadarrGrabSheet(
                release = target.row.release,
                qualityOptions = target.options.qualities,
                languageOptions = target.options.languages,
                busy = state.grabbing,
                onGrab = vm::grab,
                onDismiss = vm::dismissGrabSheet,
            )
        }
    }
}

/** Interactive import destination: connection spinner, then the screen. */
@Composable
private fun InteractiveImportDestination(
    serverId: ServerId,
    movieId: Int,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repositories = rememberRepositories(graph, serverId)
    var repository by remember(serverId) {
        mutableStateOf<RadarrInteractiveImportRepository?>(null)
    }
    LaunchedEffect(serverId, movieId) {
        repository = repositories.import()
    }
    val wiredRepository = repository
        ?: return Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    val vm: InteractiveImportViewModel = viewModel(
        key = "radarr-import-flow-" + serverId.value + "-" + movieId,
    ) {
        InteractiveImportViewModel(movieId = movieId, repository = wiredRepository)
    }
    val state by vm.state.collectAsState()
    val navigator = LocalArrShellNavigator.current
    InteractiveImportScreen(
        state = state,
        onBack = remember(navigator) { { navigator.back() } },
        onRetryMovie = vm::loadMovie,
        onOpenFolder = remember(vm) { { folder -> vm.browse(folder.path) } },
        onPickFolder = vm::pickFolder,
        onRetryFolders = vm::retryFolders,
        onRetryFiles = vm::retryFiles,
        onToggleFile = vm::toggleFile,
        onSelectAll = vm::selectAll,
        onOpenEditor = vm::openEditor,
        onCloseEditor = vm::closeEditor,
        onApplyDecision = vm::applyDecision,
        onApprove = vm::approve,
        onConsumeNotice = vm::consumeNotice,
        modifier = modifier,
    )
}
