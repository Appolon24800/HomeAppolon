package dev.appolon.homelab.arr.feature.radarr.addnew

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
import dev.appolon.homelab.arr.feature.radarr.addnew.data.RadarrAddNewRepository
import dev.appolon.homelab.arr.feature.radarr.addnew.data.RadarrCollectionsRepository
import dev.appolon.homelab.arr.feature.radarr.addnew.ui.RadarrAddNewScreen
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.AddNewTab
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.AddNewViewModel
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.RadarrCollectionsViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator

/**
 * Shell integration for the Radarr Add new feature: the shell's built-in
 * `add` slot for Radarr, one nested route for deep-linking straight to the
 * Collections tab, and open helpers. The integration layer calls [register]
 * once before composing the shell and passes [radarrAddNewShellContent] to
 * it. Foreign feature owners fall through so a composing integration can
 * chain several feature slots.
 */
object RadarrAddNewFeature {
    const val ROUTE_OWNER = "radarr-addnew"

    /** Collections browse of one server, the nested destination of this feature. */
    const val COLLECTIONS_PATTERN = "collections/{serverId}"

    val PATTERNS = listOf(COLLECTIONS_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun collectionsRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(COLLECTIONS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { path -> ArrRoute.Feature(ROUTE_OWNER, path) }

    fun openCollections(navigator: ArrShellNavigator, serverId: ServerId) {
        collectionsRoute(serverId)?.let(navigator::open)
    }
}

/**
 * Shell content for this feature: the `add` slot of `ArrRoute.Add` for
 * Radarr plus the feature slot handling this module's nested routes.
 */
fun radarrAddNewShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    add = { route ->
        if (route.app == AppType.RADARR) {
            RadarrAddNewDestination(route.serverId, graph, initialTab = AddNewTab.DISCOVER)
        }
    },
    feature = { route -> RadarrAddNewFeatureSlot(route, graph) },
)

/**
 * The Add new screen of one Radarr server, wired to the [ArrDataGraph]
 * singletons. The connection resolves asynchronously (registry read plus key
 * decryption); a spinner covers that gap, then the ViewModels build on the
 * connected API bundle and the authenticated Coil loader.
 */
@Composable
fun RadarrAddNewDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
    initialTab: AddNewTab = AddNewTab.DISCOVER,
    serverName: String? = null,
) {
    val connection = remember(serverId) {
        RadarrAddNewConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<RadarrAddNewApis?>(null) }
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
        RadarrAddNewRepository(
            serverId = serverId,
            store = graph.entityStore,
            movieLookupApi = apis.movieLookupApi,
            movieApi = apis.movieApi,
            movieImportApi = apis.movieImportApi,
            importListMoviesApi = apis.importListMoviesApi,
            rootFolderApi = apis.rootFolderApi,
            tagApi = apis.tagApi,
            qualityProfileApi = apis.qualityProfileApi,
            actionQueue = graph.actionQueue,
        )
    }
    val addViewModel: AddNewViewModel = viewModel(key = "radarr-addnew-" + serverId.value) {
        AddNewViewModel(repository = repository)
    }
    val collectionsViewModel: RadarrCollectionsViewModel =
        viewModel(key = "radarr-addnew-collections-" + serverId.value) {
            RadarrCollectionsViewModel(
                repository = RadarrCollectionsRepository(
                    serverId = serverId,
                    store = graph.entityStore,
                    collectionApi = apis.collectionApi,
                    movieApi = apis.movieApi,
                    commandRunner = apis.commandRunner,
                    actionQueue = graph.actionQueue,
                ),
                addNewRepository = repository,
            )
        }
    val state by addViewModel.state.collectAsState()
    val collectionsState by collectionsViewModel.state.collectAsState()
    if (state.tab == AddNewTab.DISCOVER && initialTab == AddNewTab.COLLECTIONS) {
        LaunchedEffect(serverId) { addViewModel.setTab(AddNewTab.COLLECTIONS) }
    }

    RadarrAddNewScreen(
        state = state,
        collectionsState = collectionsState,
        baseUrl = apis.baseUrl,
        imageLoader = imageLoader,
        serverName = serverName,
        onQueryChange = addViewModel::onQueryChange,
        onSearchSubmit = addViewModel::submitSearch,
        onRefresh = remember(addViewModel, collectionsViewModel, state.tab) {
            {
                addViewModel.refreshAll()
                if (state.tab == AddNewTab.COLLECTIONS) collectionsViewModel.refresh()
            }
        },
        onTabSelect = addViewModel::setTab,
        onOpenAddSheet = addViewModel::openAddSheet,
        onAddSheetChange = addViewModel::updateAddSheet,
        onAddSheetDismiss = addViewModel::dismissAddSheet,
        onAddSubmit = addViewModel::submitAdd,
        onConsumeMessage = addViewModel::consumeMessage,
        onCollectionsRefresh = collectionsViewModel::refresh,
        onToggleCollectionMonitored = collectionsViewModel::toggleMonitored,
        onOpenCollectionEditor = collectionsViewModel::openEditor,
        onCollectionEditorChange = collectionsViewModel::updateEditor,
        onCollectionEditorDismiss = collectionsViewModel::dismissEditor,
        onCollectionEditorSubmit = collectionsViewModel::submitEditor,
        onSearchCollection = collectionsViewModel::searchMissing,
        onConsumeCollectionsMessage = collectionsViewModel::consumeMessage,
        modifier = modifier,
    )
}

/**
 * Feature-slot entry: renders this module's nested routes and defers to
 * foreign owners by rendering nothing, so the integration layer can stack
 * several feature slots in one shell content.
 */
@Composable
fun RadarrAddNewFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != RadarrAddNewFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val collections = matchRoutePattern(RadarrAddNewFeature.COLLECTIONS_PATTERN, segments)
    if (collections != null) {
        RadarrAddNewDestination(
            serverId = ServerId(collections.getValue("serverId")),
            graph = graph,
            modifier = modifier,
            initialTab = AddNewTab.COLLECTIONS,
        )
    }
}
