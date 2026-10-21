package dev.appolon.homelab.arr.feature.lidarr.search

import androidx.compose.foundation.layout.Box
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
import dev.appolon.homelab.arr.feature.lidarr.search.data.LidarrInteractiveImportRepository
import dev.appolon.homelab.arr.feature.lidarr.search.data.LidarrReleaseRepository
import dev.appolon.homelab.arr.feature.lidarr.search.data.ReleaseScope
import dev.appolon.homelab.arr.feature.lidarr.search.ui.InteractiveImportScreen
import dev.appolon.homelab.arr.feature.lidarr.search.ui.ReleaseListScreen
import dev.appolon.homelab.arr.feature.lidarr.search.ui.LidarrGrabSheet
import dev.appolon.homelab.arr.feature.lidarr.search.vm.GrabOptions
import dev.appolon.homelab.arr.feature.lidarr.search.vm.InteractiveImportViewModel
import dev.appolon.homelab.arr.feature.lidarr.search.vm.ReleaseListViewModel
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Routes this group owns, open helpers for the other Lidarr groups (detail,
 * wanted, calendar), and the shell feature slot rendering them. The
 * integration layer calls [register] once before composing the shell; the
 * grab sheet itself is a plain composable any group can host directly.
 */
object LidarrSearchFeature {
    /** Distinct owner so this group's slot chains next to its siblings'. */
    const val ROUTE_OWNER = "lidarr-search"

    /** Interactive search results of a whole artist. */
    const val ARTIST_RELEASES_PATTERN = "releases/{serverId}/artist/{artistId}"

    /** Interactive search results of one album (its release packs). */
    const val ALBUM_RELEASES_PATTERN = "releases/{serverId}/album/{albumId}"

    /** Artist-scoped interactive import decision flow. */
    const val IMPORT_PATTERN = "import/{serverId}/artist/{artistId}"

    val PATTERNS = listOf(
        ARTIST_RELEASES_PATTERN,
        ALBUM_RELEASES_PATTERN,
        IMPORT_PATTERN,
    )

    /** Idempotent registration of every nested pattern this group owns. */
    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun artistReleasesRoute(serverId: ServerId, artistId: Int): ArrRoute.Feature? =
        featureRoute(
            ARTIST_RELEASES_PATTERN,
            mapOf("serverId" to serverId.value, "artistId" to artistId.toString()),
        )

    fun albumReleasesRoute(serverId: ServerId, albumId: Int): ArrRoute.Feature? =
        featureRoute(
            ALBUM_RELEASES_PATTERN,
            mapOf("serverId" to serverId.value, "albumId" to albumId.toString()),
        )

    fun interactiveImportRoute(serverId: ServerId, artistId: Int): ArrRoute.Feature? =
        featureRoute(
            IMPORT_PATTERN,
            mapOf("serverId" to serverId.value, "artistId" to artistId.toString()),
        )

    /** Opens the whole-artist interactive search; entry point for detail and wanted. */
    fun openArtistReleases(navigator: ArrShellNavigator, serverId: ServerId, artistId: Int) {
        artistReleasesRoute(serverId, artistId)?.let(navigator::open)
    }

    /** Opens one album's interactive search; entry point for detail, wanted, and calendar. */
    fun openAlbumReleases(navigator: ArrShellNavigator, serverId: ServerId, albumId: Int) {
        albumReleasesRoute(serverId, albumId)?.let(navigator::open)
    }

    /** Opens the artist-scoped interactive import decision flow. */
    fun openInteractiveImport(navigator: ArrShellNavigator, serverId: ServerId, artistId: Int) {
        interactiveImportRoute(serverId, artistId)?.let(navigator::open)
    }

    private fun featureRoute(pattern: String, values: Map<String, String>): ArrRoute.Feature? {
        val path = fillPattern(pattern, values) ?: return null
        return ArrRoute.Feature(ROUTE_OWNER, path)
    }
}

/** Everything the search screens need from the app's singletons. */
class LidarrSearchDeps(
    val registry: ServerRegistry,
    val secrets: ArrSecrets,
    val entityStore: EntityStore,
    val actionQueue: ActionQueue,
    val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {
    companion object {
        fun from(graph: ArrDataGraph) = LidarrSearchDeps(
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
fun ArrShellContent.withLidarrSearch(
    deps: LidarrSearchDeps,
    registry: ArrRouteRegistry,
): ArrShellContent {
    LidarrSearchFeature.register(registry)
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
            if (route.owner == LidarrSearchFeature.ROUTE_OWNER) {
                LidarrSearchFeatureSlot(route, deps)
            } else {
                previousFeature?.invoke(route)
            }
        },
    )
}

/**
 * Feature-slot entry: renders this group's nested routes. Foreign owners
 * render nothing here so a composing integration can stack several feature
 * slots in one shell content; wire [withLidarrSearch] instead for chaining.
 */
@Composable
fun LidarrSearchFeatureSlot(
    route: ArrRoute.Feature,
    deps: LidarrSearchDeps,
    modifier: Modifier = Modifier,
) {
    if (route.owner != LidarrSearchFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val artistReleases = matchRoutePattern(LidarrSearchFeature.ARTIST_RELEASES_PATTERN, segments)
    val albumReleases = matchRoutePattern(LidarrSearchFeature.ALBUM_RELEASES_PATTERN, segments)
    val import = matchRoutePattern(LidarrSearchFeature.IMPORT_PATTERN, segments)
    when {
        artistReleases != null -> ReleasesDestination(
            serverId = ServerId(artistReleases.getValue("serverId")),
            scope = ReleaseScope.Artist(
                artistReleases.getValue("artistId").toIntOrNull() ?: return,
            ),
            deps = deps,
            modifier = modifier,
        )

        albumReleases != null -> ReleasesDestination(
            serverId = ServerId(albumReleases.getValue("serverId")),
            scope = ReleaseScope.Album(
                albumReleases.getValue("albumId").toIntOrNull() ?: return,
            ),
            deps = deps,
            modifier = modifier,
        )

        import != null -> ImportDestination(
            serverId = ServerId(import.getValue("serverId")),
            artistId = import.getValue("artistId").toIntOrNull() ?: return,
            deps = deps,
            modifier = modifier,
        )
    }
}

/** Both repositories resolved for one server, ready for the ViewModels. */
private class SearchWiring(
    val releases: LidarrReleaseRepository,
    val import: LidarrInteractiveImportRepository,
)

@Composable
private fun rememberApis(
    deps: LidarrSearchDeps,
    serverId: ServerId,
): LidarrSearchApis? {
    var apis by remember(serverId) { mutableStateOf<LidarrSearchApis?>(null) }
    LaunchedEffect(serverId) {
        apis = LidarrSearchConnection(
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
    ArrLceBox(
        state = LceState.Loading,
        modifier = modifier.fillMaxSize(),
    ) {}
}

/**
 * Interactive search destination: connection spinner, then the release list
 * with its grab sheet. The pack toggle is hidden for album scope, where
 * Lidarr only returns that album's releases.
 */
@Composable
private fun ReleasesDestination(
    serverId: ServerId,
    scope: ReleaseScope,
    deps: LidarrSearchDeps,
    modifier: Modifier = Modifier,
) {
    val apis = rememberApis(deps, serverId) ?: return SearchLoading(modifier)
    val wiring = remember(serverId, apis) {
        SearchWiring(
            releases = LidarrReleaseRepository(
                serverId = serverId,
                store = deps.entityStore,
                releaseApi = apis.releaseApi,
                releasePushApi = apis.releasePushApi,
                actionQueue = deps.actionQueue,
            ),
            import = LidarrInteractiveImportRepository(
                serverId = serverId,
                store = deps.entityStore,
                manualImportApi = apis.manualImportApi,
                fileSystemCall = { path, includeFiles ->
                    apis.fileSystemApi.getFileSystem(path = path, includeFiles = includeFiles)
                },
                artistById = { id -> apis.artistApi.getArtist(id) },
                albumById = { id -> apis.albumApi.getAlbum(id) },
                qualityProfileById = { id -> apis.qualityProfileApi.getQualityProfileById(id) },
                actionQueue = deps.actionQueue,
            ),
        )
    }
    val vm: ReleaseListViewModel = viewModel(
        key = "lidarr-releases-" + serverId.value + "-" + scope.cacheKey(),
    ) {
        ReleaseListViewModel(
            scope = scope,
            repository = wiring.releases,
            grabOptionsSource = {
                // Album scope resolves its artist through the album cache,
                // because the artist's quality profile holds the options.
                val artistId = when (scope) {
                    is ReleaseScope.Artist -> scope.artistId
                    is ReleaseScope.Album -> when (
                        val album = wiring.import.album(scope.albumId)
                    ) {
                        is ArrResult.Ok -> album.value.artistId.takeIf { it > 0 }
                        is ArrResult.Err -> null
                    }
                }
                val artist = when (val found = artistId?.let { wiring.import.artist(it) }) {
                    is ArrResult.Ok -> found
                    else -> null
                }
                if (artist == null) {
                    GrabOptions()
                } else {
                    GrabOptions(qualities = wiring.import.qualityOptions(artist.value))
                }
            },
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val queuedArtistIds by vm.queuedArtistIds.collectAsStateWithLifecycle()
    val queuedAlbumIds by vm.queuedAlbumIds.collectAsStateWithLifecycle()
    val queued = when (scope) {
        is ReleaseScope.Artist -> scope.artistId in queuedArtistIds
        is ReleaseScope.Album -> scope.albumId in queuedAlbumIds
    }
    val navigator = LocalArrShellNavigator.current
    Box(modifier) {
        ReleaseListScreen(
            state = state,
            queued = queued,
            showGroupFilter = scope is ReleaseScope.Artist,
            onRefresh = vm::refresh,
            onRetry = vm::retry,
            onBack = { navigator.back() },
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
            LidarrGrabSheet(
                release = target.row.release,
                qualityOptions = target.options.qualities,
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
    artistId: Int,
    deps: LidarrSearchDeps,
    modifier: Modifier = Modifier,
) {
    val apis = rememberApis(deps, serverId) ?: return SearchLoading(modifier)
    val repository = remember(serverId, apis, artistId) {
        LidarrInteractiveImportRepository(
            serverId = serverId,
            store = deps.entityStore,
            manualImportApi = apis.manualImportApi,
            fileSystemCall = { path, includeFiles ->
                apis.fileSystemApi.getFileSystem(path = path, includeFiles = includeFiles)
            },
            artistById = { id -> apis.artistApi.getArtist(id) },
            albumById = { id -> apis.albumApi.getAlbum(id) },
            qualityProfileById = { id -> apis.qualityProfileApi.getQualityProfileById(id) },
            actionQueue = deps.actionQueue,
        )
    }
    val vm: InteractiveImportViewModel = viewModel(
        key = "lidarr-import-flow-" + serverId.value + "-" + artistId,
    ) {
        InteractiveImportViewModel(artistId = artistId, repository = repository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current
    InteractiveImportScreen(
        state = state,
        onBack = { navigator.back() },
        onRetryArtist = vm::loadArtist,
        onOpenFolder = { folder -> vm.browse(folder.path) },
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
