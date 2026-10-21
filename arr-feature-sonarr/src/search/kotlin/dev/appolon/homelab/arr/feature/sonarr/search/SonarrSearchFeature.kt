package dev.appolon.homelab.arr.feature.sonarr.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.sonarr.search.data.SonarrInteractiveImportRepository
import dev.appolon.homelab.arr.feature.sonarr.search.data.SonarrReleaseRepository
import dev.appolon.homelab.arr.feature.sonarr.search.data.ReleaseScope
import dev.appolon.homelab.arr.feature.sonarr.search.ui.InteractiveImportScreen
import dev.appolon.homelab.arr.feature.sonarr.search.ui.ReleaseListScreen
import dev.appolon.homelab.arr.feature.sonarr.search.ui.SonarrGrabSheet
import dev.appolon.homelab.arr.feature.sonarr.search.vm.GrabOptions
import dev.appolon.homelab.arr.feature.sonarr.search.vm.InteractiveImportViewModel
import dev.appolon.homelab.arr.feature.sonarr.search.vm.ReleaseListViewModel
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Routes this group owns, open helpers for the other Sonarr groups (detail,
 * wanted, calendar), and the shell feature slot rendering them. The
 * integration layer calls [register] once before composing the shell; the
 * grab sheet itself is a plain composable any group can host directly.
 */
object SonarrSearchFeature {
    /** Distinct owner so this group's slot chains next to its siblings'. */
    const val ROUTE_OWNER = "sonarr-search"

    /** Interactive search results of a whole series. */
    const val SERIES_RELEASES_PATTERN = "releases/{serverId}/{seriesId}"

    /** Interactive search results of one season (packs plus episodes). */
    const val SEASON_RELEASES_PATTERN = "releases/{serverId}/{seriesId}/season/{seasonNumber}"

    /** Interactive search results of one episode. */
    const val EPISODE_RELEASES_PATTERN = "releases/{serverId}/{seriesId}/episode/{episodeId}"

    /** Series-scoped interactive import decision flow. */
    const val IMPORT_PATTERN = "import/{serverId}/{seriesId}"

    /** Interactive import decision flow pinned to one season. */
    const val IMPORT_SEASON_PATTERN = "import/{serverId}/{seriesId}/season/{seasonNumber}"

    val PATTERNS = listOf(
        SERIES_RELEASES_PATTERN,
        SEASON_RELEASES_PATTERN,
        EPISODE_RELEASES_PATTERN,
        IMPORT_PATTERN,
        IMPORT_SEASON_PATTERN,
    )

    /** Idempotent registration of every nested pattern this group owns. */
    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun seriesReleasesRoute(serverId: ServerId, seriesId: Int): ArrRoute.Feature? =
        featureRoute(
            SERIES_RELEASES_PATTERN,
            mapOf("serverId" to serverId.value, "seriesId" to seriesId.toString()),
        )

    fun seasonReleasesRoute(
        serverId: ServerId,
        seriesId: Int,
        seasonNumber: Int,
    ): ArrRoute.Feature? =
        featureRoute(
            SEASON_RELEASES_PATTERN,
            mapOf(
                "serverId" to serverId.value,
                "seriesId" to seriesId.toString(),
                "seasonNumber" to seasonNumber.toString(),
            ),
        )

    fun episodeReleasesRoute(serverId: ServerId, seriesId: Int, episodeId: Int): ArrRoute.Feature? =
        featureRoute(
            EPISODE_RELEASES_PATTERN,
            mapOf(
                "serverId" to serverId.value,
                "seriesId" to seriesId.toString(),
                "episodeId" to episodeId.toString(),
            ),
        )

    /**
     * Route of the interactive import flow; a [seasonNumber] of null starts
     * the series-wide flow, otherwise the flow pins that season.
     */
    fun interactiveImportRoute(
        serverId: ServerId,
        seriesId: Int,
        seasonNumber: Int? = null,
    ): ArrRoute.Feature? {
        val values = if (seasonNumber == null) {
            mapOf("serverId" to serverId.value, "seriesId" to seriesId.toString())
        } else {
            mapOf(
                "serverId" to serverId.value,
                "seriesId" to seriesId.toString(),
                "seasonNumber" to seasonNumber.toString(),
            )
        }
        return featureRoute(
            if (seasonNumber == null) IMPORT_PATTERN else IMPORT_SEASON_PATTERN,
            values,
        )
    }

    /** Opens the whole-series interactive search; entry point for detail and wanted. */
    fun openSeriesReleases(navigator: ArrShellNavigator, serverId: ServerId, seriesId: Int) {
        seriesReleasesRoute(serverId, seriesId)?.let(navigator::open)
    }

    /** Opens one season's interactive search with the pack/episode toggle. */
    fun openSeasonReleases(
        navigator: ArrShellNavigator,
        serverId: ServerId,
        seriesId: Int,
        seasonNumber: Int,
    ) {
        seasonReleasesRoute(serverId, seriesId, seasonNumber)?.let(navigator::open)
    }

    /** Opens one episode's interactive search; entry point for wanted and calendar. */
    fun openEpisodeReleases(
        navigator: ArrShellNavigator,
        serverId: ServerId,
        seriesId: Int,
        episodeId: Int,
    ) {
        episodeReleasesRoute(serverId, seriesId, episodeId)?.let(navigator::open)
    }

    /** Opens the series- or season-scoped interactive import decision flow. */
    fun openInteractiveImport(
        navigator: ArrShellNavigator,
        serverId: ServerId,
        seriesId: Int,
        seasonNumber: Int? = null,
    ) {
        interactiveImportRoute(serverId, seriesId, seasonNumber)?.let(navigator::open)
    }

    private fun featureRoute(pattern: String, values: Map<String, String>): ArrRoute.Feature? {
        val path = fillPattern(pattern, values) ?: return null
        return ArrRoute.Feature(ROUTE_OWNER, path)
    }
}

/** Everything the search screens need from the app's singletons. */
class SonarrSearchDeps(
    val registry: ServerRegistry,
    val secrets: ArrSecrets,
    val entityStore: EntityStore,
    val actionQueue: ActionQueue,
    val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {
    companion object {
        fun from(graph: ArrDataGraph) = SonarrSearchDeps(
            registry = graph.registry,
            secrets = graph.secrets,
            entityStore = graph.entityStore,
            actionQueue = graph.actionQueue,
        )
    }
}

/**
 * Fills the shell's feature slot for this group's nested routes, leaving
 * every other owner untouched so sibling feature modules can wrap the same
 * content object.
 */
fun ArrShellContent.withSonarrSearch(
    deps: SonarrSearchDeps,
    registry: ArrRouteRegistry,
): ArrShellContent {
    SonarrSearchFeature.register(registry)
    val previousFeature = feature
    // ArrShellContent has no copy(); rebuild it slot by slot so sibling
    // features can wrap the same content object.
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
            if (route.owner == SonarrSearchFeature.ROUTE_OWNER) {
                SonarrSearchFeatureSlot(route, deps)
            } else {
                previousFeature?.invoke(route)
            }
        },
    )
}

/**
 * Feature-slot entry: renders this group's nested routes. Foreign owners
 * render nothing here so a composing integration can stack several feature
 * slots in one shell content; wire [withSonarrSearch] instead for chaining.
 */
@Composable
fun SonarrSearchFeatureSlot(
    route: ArrRoute.Feature,
    deps: SonarrSearchDeps,
    modifier: Modifier = Modifier,
) {
    if (route.owner != SonarrSearchFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val seriesReleases = matchRoutePattern(SonarrSearchFeature.SERIES_RELEASES_PATTERN, segments)
    val seasonReleases = matchRoutePattern(SonarrSearchFeature.SEASON_RELEASES_PATTERN, segments)
    val episodeReleases = matchRoutePattern(SonarrSearchFeature.EPISODE_RELEASES_PATTERN, segments)
    val import = matchRoutePattern(SonarrSearchFeature.IMPORT_PATTERN, segments)
    val seasonImport = matchRoutePattern(SonarrSearchFeature.IMPORT_SEASON_PATTERN, segments)
    when {
        seriesReleases != null -> ReleasesDestination(
            serverId = ServerId(seriesReleases.getValue("serverId")),
            scope = ReleaseScope.Series(
                seriesReleases.getValue("seriesId").toIntOrNull() ?: return,
            ),
            deps = deps,
            modifier = modifier,
        )

        seasonReleases != null -> ReleasesDestination(
            serverId = ServerId(seasonReleases.getValue("serverId")),
            scope = ReleaseScope.Season(
                seriesId = seasonReleases.getValue("seriesId").toIntOrNull() ?: return,
                seasonNumber = seasonReleases.getValue("seasonNumber").toIntOrNull() ?: return,
            ),
            deps = deps,
            modifier = modifier,
        )

        episodeReleases != null -> ReleasesDestination(
            serverId = ServerId(episodeReleases.getValue("serverId")),
            scope = ReleaseScope.Episode(
                seriesId = episodeReleases.getValue("seriesId").toIntOrNull() ?: return,
                episodeId = episodeReleases.getValue("episodeId").toIntOrNull() ?: return,
            ),
            deps = deps,
            modifier = modifier,
        )

        import != null -> ImportDestination(
            serverId = ServerId(import.getValue("serverId")),
            seriesId = import.getValue("seriesId").toIntOrNull() ?: return,
            seasonNumber = null,
            deps = deps,
            modifier = modifier,
        )

        seasonImport != null -> ImportDestination(
            serverId = ServerId(seasonImport.getValue("serverId")),
            seriesId = seasonImport.getValue("seriesId").toIntOrNull() ?: return,
            seasonNumber = seasonImport.getValue("seasonNumber").toIntOrNull(),
            deps = deps,
            modifier = modifier,
        )
    }
}

/** Both repositories resolved for one server, ready for the ViewModels. */
private class SearchWiring(
    val releases: SonarrReleaseRepository,
    val import: SonarrInteractiveImportRepository,
)

@Composable
private fun rememberApis(
    deps: SonarrSearchDeps,
    serverId: ServerId,
): SonarrSearchApis? {
    var apis by remember(serverId) { mutableStateOf<SonarrSearchApis?>(null) }
    LaunchedEffect(serverId) {
        apis = SonarrSearchConnection(
            registry = deps.registry,
            secrets = deps.secrets,
            clientFactory = deps.clientFactory,
        ).apis(serverId)
    }
    return apis
}

/** Loading pane shown while the per-server connection resolves. */
@Composable
private fun SearchLoading(modifier: Modifier = Modifier) {
    dev.appolon.homelab.arr.ui.lce.ArrLceBox(
        state = dev.appolon.homelab.arr.ui.lce.LceState.Loading,
        modifier = modifier.fillMaxSize(),
    ) {}
}

/**
 * Interactive search destination: connection spinner, then the release list
 * with its grab sheet. The group toggle is hidden for episode scope, where
 * Sonarr only returns episode matches.
 */
@Composable
private fun ReleasesDestination(
    serverId: ServerId,
    scope: ReleaseScope,
    deps: SonarrSearchDeps,
    modifier: Modifier = Modifier,
) {
    val apis = rememberApis(deps, serverId) ?: return SearchLoading(modifier)
    val wiring = remember(serverId, apis) {
        SearchWiring(
            releases = SonarrReleaseRepository(
                serverId = serverId,
                store = deps.entityStore,
                releaseApi = apis.releaseApi,
                releasePushApi = apis.releasePushApi,
                actionQueue = deps.actionQueue,
            ),
            import = SonarrInteractiveImportRepository(
                serverId = serverId,
                store = deps.entityStore,
                manualImportApi = apis.manualImportApi,
                fileSystemCall = { path, includeFiles ->
                    apis.fileSystemApi.list(path = path, includeFiles = includeFiles)
                },
                seriesById = { id -> apis.seriesApi.get(id) },
                languageTable = { apis.languageApi.list() },
                qualityProfileById = { id -> apis.qualityProfileApi.get(id).body() },
                actionQueue = deps.actionQueue,
            ),
        )
    }
    val vm: ReleaseListViewModel = viewModel(
        key = "sonarr-releases-" + serverId.value + "-" + scope.cacheKey(),
    ) {
        ReleaseListViewModel(
            scope = scope,
            repository = wiring.releases,
            grabOptionsSource = {
                when (val series = wiring.import.series(scope.seriesId)) {
                    is ArrResult.Ok -> GrabOptions(
                        qualities = wiring.import.qualityOptions(series.value),
                        languages = wiring.import.languageOptions(),
                    )

                    is ArrResult.Err -> GrabOptions()
                }
            },
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val queuedSeriesIds by vm.queuedSeriesIds.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current
    androidx.compose.foundation.layout.Box(modifier) {
        ReleaseListScreen(
            state = state,
            queued = scope.seriesId in queuedSeriesIds,
            showGroupFilter = scope !is ReleaseScope.Episode,
            onRefresh = vm::refresh,
            onRetry = vm::retry,
            onBack = remember(navigator) { { navigator.back() } },
            onSort = vm::setSort,
            onGroupFilter = vm::setGroupFilter,
            onToggleRejections = vm::toggleRejections,
            onGrab = vm::openGrabSheet,
            onPushExternal = vm::openPushDialog,
            onPushDialogDismiss = vm::dismissPushDialog,
            onPushConfirm = vm::pushExternalRelease,
            onConsumeMessage = vm::consumeMessage,
        )
        val target = state.grabTarget
        if (target != null) {
            SonarrGrabSheet(
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
private fun ImportDestination(
    serverId: ServerId,
    seriesId: Int,
    seasonNumber: Int?,
    deps: SonarrSearchDeps,
    modifier: Modifier = Modifier,
) {
    val apis = rememberApis(deps, serverId) ?: return SearchLoading(modifier)
    val repository = remember(serverId, apis, seasonNumber) {
        SonarrInteractiveImportRepository(
            seasonNumber = seasonNumber,
            serverId = serverId,
            store = deps.entityStore,
            manualImportApi = apis.manualImportApi,
            fileSystemCall = { path, includeFiles ->
                apis.fileSystemApi.list(path = path, includeFiles = includeFiles)
            },
            seriesById = { id -> apis.seriesApi.get(id) },
            languageTable = { apis.languageApi.list() },
            qualityProfileById = { id -> apis.qualityProfileApi.get(id).body() },
            actionQueue = deps.actionQueue,
        )
    }
    val vm: InteractiveImportViewModel = viewModel(
        key = "sonarr-import-flow-" + serverId.value + "-" + seriesId + "-" + (seasonNumber ?: 0),
    ) {
        InteractiveImportViewModel(seriesId = seriesId, repository = repository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current
    InteractiveImportScreen(
        state = state,
        onBack = remember(navigator) { { navigator.back() } },
        onRetrySeries = vm::loadSeries,
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
