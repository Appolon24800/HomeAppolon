package dev.appolon.homelab.arr.feature.lidarr.library

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
import coil3.ImageLoader
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.lidarr.library.data.LidarrArtistsRepository
import dev.appolon.homelab.arr.feature.lidarr.library.data.SavedArtistFiltersStore
import dev.appolon.homelab.arr.feature.lidarr.library.ui.ArtistLibraryScreen
import dev.appolon.homelab.arr.feature.lidarr.library.vm.ArtistLibraryViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Shell integration for the Lidarr artists library: the nested routes this
 * module owns, open helpers, and the shell slot composables. The integration
 * layer calls [register] once before composing the shell and passes
 * [lidarrLibraryShellContent] into it. Detail navigation is a callback slot:
 * the default opens the shell's built-in detail route, which renders the
 * shell placeholder until the detail feature lands.
 */
object LidarrLibraryFeature {
    const val ROUTE_OWNER = "lidarr-library"

    /** Library destination for one server, server id embedded in the path. */
    const val ARTISTS_PATTERN = "artists/{serverId}"

    /** Same library with one saved filter applied on entry. */
    const val ARTISTS_FILTER_PATTERN = "artists/{serverId}/filter/{filterId}"

    val PATTERNS = listOf(ARTISTS_PATTERN, ARTISTS_FILTER_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openArtists(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(ARTISTS_PATTERN, mapOf("serverId" to serverId.value)) ?: ARTISTS_PATTERN
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }
}

/**
 * Shell content for this module: the library slot of `ArrRoute.Library` for
 * Lidarr and the feature slot handling this module's nested routes. Foreign
 * feature owners fall through so a composing integration can chain slots.
 */
fun lidarrLibraryShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    library = { route ->
        if (route.app == AppType.LIDARR) {
            LidarrLibraryDestination(route.serverId, graph)
        }
    },
    feature = { route -> LidarrLibraryFeatureSlot(route, graph) },
)

/**
 * The artists library of one Lidarr server, wired to the [ArrDataGraph]
 * singletons. The connection resolves asynchronously (registry read plus
 * key decryption); a spinner covers that gap, then the ViewModel builds on
 * the connected API bundle and the authenticated Coil loader.
 */
@Composable
fun LidarrLibraryDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
    initialFilterId: String? = null,
) {
    val connection = remember(serverId) {
        LidarrLibraryConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<LidarrLibraryClient?>(null) }
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
    val vm: ArtistLibraryViewModel = viewModel(key = "lidarr-library-" + serverId.value) {
        ArtistLibraryViewModel(
            repository = LidarrArtistsRepository(
                serverId = serverId,
                store = graph.entityStore,
                artistApi = apis.artistApi,
                artistEditorApi = apis.artistEditorApi,
                albumApi = apis.albumApi,
                albumStudioApi = apis.albumStudioApi,
                qualityProfileApi = apis.qualityProfiles,
                metadataProfileApi = apis.metadataProfiles,
                commandRunner = apis.commandRunner,
                actionQueue = graph.actionQueue,
            ),
            savedFilters = SavedArtistFiltersStore(graph.entityStore, serverId),
        )
    }
    LaunchedEffect(initialFilterId) {
        initialFilterId?.let { vm.applySavedFilter(it) }
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()
    val navigator = LocalArrShellNavigator.current

    ArtistLibraryScreen(
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
        onArtistActivate = { artistId ->
            if (state.selectMode) {
                vm.toggleSelected(artistId)
            } else {
                navigator.open(ArrRoute.Detail(serverId, AppType.LIDARR, artistId.toString()))
            }
        },
        onArtistLongClick = vm::enterSelectMode,
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
fun LidarrLibraryFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != LidarrLibraryFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val filtered = matchRoutePattern(LidarrLibraryFeature.ARTISTS_FILTER_PATTERN, segments)
    val artists = matchRoutePattern(LidarrLibraryFeature.ARTISTS_PATTERN, segments)
    when {
        filtered != null -> LidarrLibraryDestination(
            serverId = ServerId(filtered.getValue("serverId")),
            graph = graph,
            modifier = modifier,
            initialFilterId = filtered.getValue("filterId"),
        )

        artists != null -> LidarrLibraryDestination(
            serverId = ServerId(artists.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )
    }
}
