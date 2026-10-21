package dev.appolon.homelab.arr.feature.lidarr.addnew

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
import dev.appolon.homelab.arr.feature.lidarr.addnew.data.LidarrAddNewRepository
import dev.appolon.homelab.arr.feature.lidarr.addnew.ui.LidarrAddNewScreen
import dev.appolon.homelab.arr.feature.lidarr.addnew.vm.AddNewTab
import dev.appolon.homelab.arr.feature.lidarr.addnew.vm.AddNewViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator

/**
 * Shell integration for the Lidarr Add new feature: the shell's built-in
 * `add` slot for Lidarr, one nested route for deep-linking straight to the
 * AlbumStudio tab, and open helpers. The integration layer calls [register]
 * once before composing the shell and passes [lidarrAddNewShellContent] to
 * it. Foreign feature owners fall through so a composing integration can
 * chain several feature slots.
 */
object LidarrAddNewFeature {
    const val ROUTE_OWNER = "lidarr-addnew"

    /** Add new of one server with the studio tab preselected; deep-link target. */
    const val STUDIO_PATTERN = "addnew/{serverId}"

    val PATTERNS = listOf(STUDIO_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun studioRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(STUDIO_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { path -> ArrRoute.Feature(ROUTE_OWNER, path) }

    fun openStudio(navigator: ArrShellNavigator, serverId: ServerId) {
        studioRoute(serverId)?.let(navigator::open)
    }
}

/**
 * Shell content for this feature: the `add` slot of `ArrRoute.Add` for
 * Lidarr plus the feature slot handling this module's nested routes.
 */
fun lidarrAddNewShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    add = { route ->
        if (route.app == AppType.LIDARR) {
            LidarrAddNewDestination(route.serverId, graph)
        }
    },
    feature = { route -> LidarrAddNewFeatureSlot(route, graph) },
)

/**
 * The Add new screen of one Lidarr server, wired to the [ArrDataGraph]
 * singletons. The connection resolves asynchronously (registry read plus key
 * decryption); a spinner covers that gap, then the ViewModel builds on the
 * connected API bundle and the authenticated Coil loader.
 */
@Composable
fun LidarrAddNewDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
    initialTab: AddNewTab = AddNewTab.STUDIO,
    serverName: String? = null,
) {
    val connection = remember(serverId) {
        LidarrAddNewConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<LidarrAddNewApis?>(null) }
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
    val repository = remember(serverId, apis) {
        LidarrAddNewRepository(
            serverId = serverId,
            store = graph.entityStore,
            artistLookupApi = apis.artistLookupApi,
            artistApi = apis.artistApi,
            albumLookupApi = apis.albumLookupApi,
            albumApi = apis.albumApi,
            albumStudioApi = apis.albumStudioApi,
            importListApi = apis.importListApi,
            rootFolderApi = apis.rootFolderApi,
            tagApi = apis.tagApi,
            qualityProfileApi = apis.qualityProfileApi,
            metadataProfileApi = apis.metadataProfileApi,
            actionQueue = graph.actionQueue,
        )
    }
    val vm: AddNewViewModel = viewModel(key = "lidarr-addnew-" + serverId.value) {
        AddNewViewModel(repository = repository)
    }
    val state by vm.state.collectAsState()
    if (state.tab == AddNewTab.STUDIO && initialTab == AddNewTab.LISTS) {
        LaunchedEffect(serverId) { vm.setTab(AddNewTab.LISTS) }
    }

    LidarrAddNewScreen(
        state = state,
        baseUrl = apis.baseUrl,
        imageLoader = imageLoader,
        serverName = serverName,
        onQueryChange = vm::onQueryChange,
        onScopeChange = vm::setScope,
        onSearchSubmit = vm::submitSearch,
        onRefresh = vm::refreshAll,
        onRefreshLists = vm::refreshLists,
        onTabSelect = vm::setTab,
        onOpenArtistAddSheet = vm::openArtistAddSheet,
        onOpenAlbumAddSheet = vm::openAlbumAddSheet,
        onConsumeMessage = vm::consumeMessage,
        onOpenStudioPicker = vm::openStudioPicker,
        onStudioToggleAlbum = vm::toggleStudioAlbum,
        onStudioStrategyChange = vm::setStudioStrategy,
        onStudioSelectAll = vm::studioSelectAll,
        onStudioSelectNone = vm::studioSelectNone,
        onStudioApply = vm::submitStudio,
        onStudioRetry = { state.studioPicker?.let { vm.openStudioPicker(it.artist) } },
        onStudioDismiss = vm::dismissStudioPicker,
        onArtistSheetChange = vm::updateArtistAddSheet,
        onArtistSheetDismiss = vm::dismissArtistAddSheet,
        onArtistAdd = vm::submitArtistAdd,
        onAlbumSheetChange = vm::updateAlbumAddSheet,
        onAlbumSheetDismiss = vm::dismissAlbumAddSheet,
        onAlbumAdd = vm::submitAlbumAdd,
        modifier = modifier,
    )
}

/**
 * Feature-slot entry: renders this module's nested routes and defers to
 * foreign owners by rendering nothing, so the integration layer can stack
 * several feature slots in one shell content.
 */
@Composable
fun LidarrAddNewFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != LidarrAddNewFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val studio = matchRoutePattern(LidarrAddNewFeature.STUDIO_PATTERN, segments)
    if (studio != null) {
        LidarrAddNewDestination(
            serverId = ServerId(studio.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )
    }
}
