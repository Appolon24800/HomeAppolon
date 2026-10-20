package dev.appolon.homelab.arr.feature.prowlarr.indexers

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
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ui.ApplicationsScreen
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ui.CategoriesScreen
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ui.IndexerEditorScreen
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ui.IndexersScreen
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ui.ProxiesScreen
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.ApplicationsViewModel
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.CategoriesUiState
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.CategoriesViewModel
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.IndexerEditorViewModel
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.IndexersViewModel
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.ProxiesViewModel
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Shell integration for the Prowlarr indexer feature: the nested routes
 * this source set owns, open helpers, and the shell slots. The integration
 * layer calls [register] once before composing the shell and merges
 * [prowlarrIndexersShellContent] into it.
 */
object ProwlarrIndexersFeature {
    const val ROUTE_OWNER = "prowlarr-indexers"

    /** Indexer list of one server; also serves the shell library slot. */
    const val INDEXERS_PATTERN = "indexers/{serverId}"

    /** Add flow starting at the prescreened definition picker. */
    const val INDEXER_ADD_PATTERN = "indexers/{serverId}/add"

    /** Edit one indexer. */
    const val INDEXER_EDIT_PATTERN = "indexers/{serverId}/edit/{indexerId}"

    /** Default categories manager. */
    const val CATEGORIES_PATTERN = "indexers/{serverId}/categories"

    /** Indexer proxies CRUD. */
    const val PROXIES_PATTERN = "indexers/{serverId}/proxies"

    /** Applications with sync preview and app profiles. */
    const val APPLICATIONS_PATTERN = "indexers/{serverId}/applications"

    val PATTERNS = listOf(
        INDEXERS_PATTERN,
        INDEXER_ADD_PATTERN,
        INDEXER_EDIT_PATTERN,
        CATEGORIES_PATTERN,
        PROXIES_PATTERN,
        APPLICATIONS_PATTERN,
    )

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openIndexers(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(INDEXERS_PATTERN, mapOf("serverId" to serverId.value))
            ?: INDEXERS_PATTERN
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }
}

/**
 * Shell content for this source set: the library slot renders the indexer
 * list for Prowlarr servers, and the feature slot handles this module's
 * nested routes. Foreign owners fall through so integrations can stack
 * several feature slots.
 */
fun prowlarrIndexersShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    library = { route ->
        if (route.app == AppType.PROWLARR) {
            ProwlarrIndexersDestination(route.serverId, graph)
        }
    },
    feature = { route -> ProwlarrIndexersFeatureSlot(route, graph) },
)

/**
 * The indexers list of one Prowlarr server. The connection resolves
 * asynchronously; a spinner covers the gap, then the ViewModel builds on
 * the repository.
 */
@Composable
fun ProwlarrIndexersDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val connection = remember(serverId) {
        ProwlarrIndexersConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<ProwlarrIndexersFeatureApis?>(null) }
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
    val vm: IndexersViewModel = viewModel(key = "prowlarr-indexers-" + serverId.value) {
        IndexersViewModel(
            ProwlarrIndexersRepository(
                serverId = serverId,
                store = graph.entityStore,
                apis = apis,
                actionQueue = graph.actionQueue,
            ),
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()
    val navigator = LocalArrShellNavigator.current

    IndexersScreen(
        state = state,
        lce = lce,
        onRefresh = vm::refresh,
        onViewChange = vm::setView,
        onSortSelect = vm::setSort,
        onToggleSortDescending = vm::toggleSortDescending,
        onSearchChange = vm::setSearch,
        onProtocolFilterChange = vm::setProtocolFilter,
        onPrivacyFilterChange = vm::setPrivacyFilter,
        onStateFilterChange = vm::setStateFilter,
        onTagFilterChange = vm::setTagFilter,
        onClearFilters = vm::clearFilters,
        onIndexerActivate = { indexerId ->
            if (state.selectMode) {
                vm.toggleSelected(indexerId)
            } else {
                val path = fillPattern(
                    ProwlarrIndexersFeature.INDEXER_EDIT_PATTERN,
                    mapOf("serverId" to serverId.value, "indexerId" to indexerId.toString()),
                ) ?: ProwlarrIndexersFeature.INDEXERS_PATTERN
                navigator.open(ArrRoute.Feature(ProwlarrIndexersFeature.ROUTE_OWNER, path))
            }
        },
        onIndexerLongClick = vm::enterSelectMode,
        onSelectAll = vm::selectAllPresented,
        onExitSelection = vm::exitSelectMode,
        onSetEnabled = vm::setEnabled,
        onTestIndexer = vm::testIndexer,
        onTestAll = vm::testAll,
        onOpenBulkEditor = vm::openBulkEditor,
        onBulkEditorChange = vm::updateBulkEditor,
        onBulkEditorSubmit = vm::submitBulkEditor,
        onBulkEditorDismiss = vm::closeBulkEditor,
        onRequestDelete = vm::requestDelete,
        onDeleteConfirm = vm::confirmDelete,
        onDeleteDismiss = vm::dismissDelete,
        onAddIndexer = {
            val path = fillPattern(
                ProwlarrIndexersFeature.INDEXER_ADD_PATTERN,
                mapOf("serverId" to serverId.value),
            ) ?: ProwlarrIndexersFeature.INDEXERS_PATTERN
            navigator.open(ArrRoute.Feature(ProwlarrIndexersFeature.ROUTE_OWNER, path))
        },
        onOpenCategories = {
            val path = fillPattern(
                ProwlarrIndexersFeature.CATEGORIES_PATTERN,
                mapOf("serverId" to serverId.value),
            ) ?: ProwlarrIndexersFeature.INDEXERS_PATTERN
            navigator.open(ArrRoute.Feature(ProwlarrIndexersFeature.ROUTE_OWNER, path))
        },
        onOpenProxies = {
            val path = fillPattern(
                ProwlarrIndexersFeature.PROXIES_PATTERN,
                mapOf("serverId" to serverId.value),
            ) ?: ProwlarrIndexersFeature.INDEXERS_PATTERN
            navigator.open(ArrRoute.Feature(ProwlarrIndexersFeature.ROUTE_OWNER, path))
        },
        onOpenApplications = {
            val path = fillPattern(
                ProwlarrIndexersFeature.APPLICATIONS_PATTERN,
                mapOf("serverId" to serverId.value),
            ) ?: ProwlarrIndexersFeature.INDEXERS_PATTERN
            navigator.open(ArrRoute.Feature(ProwlarrIndexersFeature.ROUTE_OWNER, path))
        },
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}

/** Feature-slot entry rendering this module's nested routes. */
@Composable
fun ProwlarrIndexersFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != ProwlarrIndexersFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val edit = matchRoutePattern(ProwlarrIndexersFeature.INDEXER_EDIT_PATTERN, segments)
    val add = matchRoutePattern(ProwlarrIndexersFeature.INDEXER_ADD_PATTERN, segments)
    val categories = matchRoutePattern(ProwlarrIndexersFeature.CATEGORIES_PATTERN, segments)
    val proxies = matchRoutePattern(ProwlarrIndexersFeature.PROXIES_PATTERN, segments)
    val applications = matchRoutePattern(ProwlarrIndexersFeature.APPLICATIONS_PATTERN, segments)
    val indexers = matchRoutePattern(ProwlarrIndexersFeature.INDEXERS_PATTERN, segments)
    when {
        edit != null -> ProwlarrIndexerEditorDestination(
            serverId = ServerId(edit.getValue("serverId")),
            editIndexerId = edit.getValue("indexerId").toIntOrNull(),
            graph = graph,
            modifier = modifier,
        )

        add != null -> ProwlarrIndexerEditorDestination(
            serverId = ServerId(add.getValue("serverId")),
            editIndexerId = null,
            graph = graph,
            modifier = modifier,
        )

        categories != null -> ProwlarrCategoriesDestination(
            serverId = ServerId(categories.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )

        proxies != null -> ProwlarrProxiesDestination(
            serverId = ServerId(proxies.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )

        applications != null -> ProwlarrApplicationsDestination(
            serverId = ServerId(applications.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )

        indexers != null -> ProwlarrIndexersDestination(
            serverId = ServerId(indexers.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )
    }
}

/** Add and edit destination wired to the graph. */
@Composable
fun ProwlarrIndexerEditorDestination(
    serverId: ServerId,
    editIndexerId: Int?,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val connection = remember(serverId) {
        ProwlarrIndexersConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<ProwlarrIndexersFeatureApis?>(null) }
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
    val vm: IndexerEditorViewModel = viewModel(
        key = "prowlarr-indexer-editor-" + serverId.value + "-" + (editIndexerId ?: 0),
    ) {
        IndexerEditorViewModel(
            repository = ProwlarrIndexersRepository(
                serverId = serverId,
                store = graph.entityStore,
                apis = apis,
                actionQueue = graph.actionQueue,
            ),
            editIndexerId = editIndexerId,
        )
    }
    val state by vm.state.collectAsState()
    val navigator = LocalArrShellNavigator.current

    IndexerEditorScreen(
        state = state,
        onBack = { navigator.back() },
        onSchemaSearchChange = vm::setSchemaSearch,
        onSchemaProtocolChange = vm::setSchemaProtocol,
        onPickSchema = vm::pickSchema,
        onBackToPicker = vm::backToPicker,
        onFieldValueChange = vm::setFieldValue,
        onNameChange = vm::setName,
        onEnableChange = vm::setEnable,
        onPriorityChange = vm::setPriority,
        onAppProfileChange = vm::setAppProfileId,
        onToggleTag = vm::toggleTagLabel,
        onToggleCategory = vm::toggleCategory,
        onApplyPreset = vm::applyPreset,
        onToggleAdvanced = vm::toggleAdvanced,
        onTest = vm::test,
        onSave = vm::save,
        onConsumeMessage = vm::consumeMessage,
        onRetry = vm::reload,
        modifier = modifier,
    )
    LaunchedEffect(state.message) {
        if (state.message is dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.IndexerEditorMessage.Saved ||
            state.message is dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.IndexerEditorMessage.SavedQueued
        ) {
            navigator.back()
        }
    }
}

/** Categories manager destination wired to the graph. */
@Composable
fun ProwlarrCategoriesDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val connection = remember(serverId) {
        ProwlarrIndexersConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<ProwlarrIndexersFeatureApis?>(null) }
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
    val vm: CategoriesViewModel = viewModel(key = "prowlarr-categories-" + serverId.value) {
        CategoriesViewModel(
            ProwlarrIndexersRepository(
                serverId = serverId,
                store = graph.entityStore,
                apis = apis,
                actionQueue = graph.actionQueue,
            ),
        )
    }
    val state by vm.state.collectAsState()
    val lce: LceState<CategoriesUiState> = state.let { s ->
        when {
            s.loadError != null && s.rows.isEmpty() -> LceState.Error(s.loadError)
            !s.loaded -> LceState.Loading
            s.rows.isEmpty() -> LceState.Empty
            else -> LceState.Content(s, refreshing = s.refreshing)
        }
    }
    val navigator = LocalArrShellNavigator.current
    CategoriesScreen(
        state = state,
        lce = lce,
        onRefresh = vm::refresh,
        onToggleExpanded = vm::toggleExpanded,
        onBack = { navigator.back() },
        modifier = modifier,
    )
}

/** Proxies destination wired to the graph. */
@Composable
fun ProwlarrProxiesDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val connection = remember(serverId) {
        ProwlarrIndexersConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<ProwlarrIndexersFeatureApis?>(null) }
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
    val vm: ProxiesViewModel = viewModel(key = "prowlarr-proxies-" + serverId.value) {
        ProxiesViewModel(
            ProwlarrIndexersRepository(
                serverId = serverId,
                store = graph.entityStore,
                apis = apis,
                actionQueue = graph.actionQueue,
            ),
        )
    }
    val state by vm.state.collectAsState()
    val lce = state.let { s ->
        when {
            s.loadError != null && s.proxies.isEmpty() -> LceState.Error(s.loadError)
            !s.loaded -> LceState.Loading
            s.proxies.isEmpty() -> LceState.Empty
            else -> LceState.Content(s, refreshing = s.refreshing)
        }
    }
    val navigator = LocalArrShellNavigator.current
    ProxiesScreen(
        state = state,
        lce = lce,
        onRefresh = vm::refresh,
        onBack = { navigator.back() },
        onAdd = vm::openAdd,
        onEdit = vm::edit,
        onEditorClose = vm::closeEditor,
        onFieldValueChange = vm::setFieldValue,
        onNameChange = vm::setName,
        onToggleTag = vm::toggleTagLabel,
        onSave = vm::save,
        onTest = vm::test,
        onRequestDelete = vm::requestDelete,
        onDeleteConfirm = vm::confirmDelete,
        onDeleteDismiss = vm::dismissDelete,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}

/** Applications destination wired to the graph. */
@Composable
fun ProwlarrApplicationsDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val connection = remember(serverId) {
        ProwlarrIndexersConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<ProwlarrIndexersFeatureApis?>(null) }
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
    val vm: ApplicationsViewModel = viewModel(key = "prowlarr-applications-" + serverId.value) {
        ApplicationsViewModel(
            ProwlarrIndexersRepository(
                serverId = serverId,
                store = graph.entityStore,
                apis = apis,
                actionQueue = graph.actionQueue,
            ),
        )
    }
    val state by vm.state.collectAsState()
    val lce = state.let { s ->
        when {
            s.loadError != null && s.applications.isEmpty() -> LceState.Error(s.loadError)
            !s.loaded -> LceState.Loading
            s.applications.isEmpty() -> LceState.Empty
            else -> LceState.Content(s, refreshing = s.refreshing)
        }
    }
    val navigator = LocalArrShellNavigator.current
    ApplicationsScreen(
        state = state,
        lce = lce,
        onRefresh = vm::refresh,
        onBack = { navigator.back() },
        onSyncAll = vm::syncAll,
        onAdd = vm::openAdd,
        onShowProfiles = vm::showProfiles,
        onOpenSyncPreview = vm::openSyncPreview,
        onCloseSyncPreview = vm::closeSyncPreview,
        onEdit = vm::edit,
        onEditorClose = vm::closeEditor,
        onFieldValueChange = vm::setFieldValue,
        onNameChange = vm::setName,
        onSyncLevelChange = vm::setSyncLevel,
        onToggleTag = vm::toggleTagLabel,
        onSave = vm::save,
        onTest = vm::test,
        onRequestDelete = vm::requestDelete,
        onDeleteConfirm = vm::confirmDelete,
        onDeleteDismiss = vm::dismissDelete,
        onOpenProfileEditor = vm::openProfileEditor,
        onCloseProfileEditor = vm::closeProfileEditor,
        onProfileNameChange = vm::setProfileName,
        onProfileMinimumSeedersChange = vm::setProfileMinimumSeeders,
        onProfileEnableRssChange = vm::setProfileEnableRss,
        onProfileEnableAutomaticSearchChange = vm::setProfileEnableAutomaticSearch,
        onProfileEnableInteractiveSearchChange = vm::setProfileEnableInteractiveSearch,
        onProfileSave = vm::saveProfile,
        onProfileRequestDelete = vm::requestProfileDelete,
        onProfileDeleteConfirm = vm::confirmProfileDelete,
        onProfileDeleteDismiss = vm::dismissProfileDelete,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}
