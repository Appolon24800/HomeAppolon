package dev.appolon.homelab.arr.feature.sonarr.library

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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.ImageLoader
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.sonarr.library.data.SavedSeriesFiltersStore
import dev.appolon.homelab.arr.feature.sonarr.library.data.SonarrSeriesRepository
import dev.appolon.homelab.arr.feature.sonarr.library.ui.SeriesLibraryContent
import dev.appolon.homelab.arr.feature.sonarr.library.vm.SeriesLibraryViewModel
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Route registration for the series library. The built-in
 * `server/{serverId}/sonarr/library` pattern is the shell's own route; this
 * registration adds the feature-owned alias `f/sonarr/library/{serverId}` so
 * other Sonarr screens and deep links can return to the series library
 * without duplicating shell knowledge.
 */
object SonarrLibraryRoutes {
    const val OWNER = "sonarr"
    const val LIBRARY_PATTERN = "library/{serverId}"

    /** Registers this feature's nested routes; false when already present. */
    fun register(registry: ArrRouteRegistry): Boolean =
        registry.register(OWNER, LIBRARY_PATTERN)

    /** Encoded nested route for one server's series library. */
    fun libraryRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(LIBRARY_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { path -> ArrRoute.Feature(OWNER, path) }

    /** Extracts the server a feature route points at, or null for other owners. */
    fun serverIdOf(route: ArrRoute.Feature): ServerId? {
        if (route.owner != OWNER) return null
        // Nested paths travel percent-encoded; decode before matching so the
        // extracted id equals the stored ServerId.
        val segments = route.path.split('/').map(::decodeSegment)
        val params = matchRoutePattern(LIBRARY_PATTERN, segments) ?: return null
        return params["serverId"]?.let(::ServerId)
    }
}

/** Strict RFC 3986 percent decoding for one path segment; malformed escapes stay literal. */
private fun decodeSegment(raw: String): String {
    if ('%' !in raw) return raw
    val bytes = ArrayList<Byte>(raw.length)
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '%' && i + 2 < raw.length) {
            val hi = hexValue(raw[i + 1])
            val lo = hexValue(raw[i + 2])
            if (hi == null || lo == null) return raw
            bytes.add(((hi shl 4) or lo).toByte())
            i += 3
        } else {
            bytes.addAll(c.toString().encodeToByteArray().toList())
            i += 1
        }
    }
    return bytes.toByteArray().decodeToString()
}

private fun hexValue(c: Char): Int? = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> null
}

/** Everything the library screen needs from the app's singletons. */
class SonarrLibraryDeps(
    val registry: ServerRegistry,
    val secrets: ArrSecrets,
    val entityStore: EntityStore,
    val actionQueue: ActionQueue,
    val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {
    companion object {
        fun from(graph: ArrDataGraph) = SonarrLibraryDeps(
            registry = graph.registry,
            secrets = graph.secrets,
            entityStore = graph.entityStore,
            actionQueue = graph.actionQueue,
        )
    }
}

/**
 * Fills the shell's library slot for Sonarr and routes this feature's nested
 * library alias to the same screen. Other apps fall through to the previous
 * slot, so sibling feature modules can wrap the same content object.
 */
fun ArrShellContent.withSonarrSeriesLibrary(
    deps: SonarrLibraryDeps,
    registry: ArrRouteRegistry,
): ArrShellContent {
    SonarrLibraryRoutes.register(registry)
    val previousLibrary = library
    val previousFeature = feature
    // ArrShellContent has no copy(); rebuild it slot by slot so sibling
    // features can wrap the same content object.
    return ArrShellContent(
        serverList = serverList,
        serverHome = serverHome,
        library = { route ->
            if (route.app == AppType.SONARR) {
                SonarrSeriesLibrary(serverId = route.serverId, deps = deps)
            } else {
                previousLibrary?.invoke(route)
            }
        },
        detail = detail,
        add = add,
        queue = queue,
        calendar = calendar,
        settings = settings,
        feature = { route ->
            val serverId = SonarrLibraryRoutes.serverIdOf(route)
            if (serverId != null) {
                SonarrSeriesLibrary(serverId = serverId, deps = deps)
            } else {
                previousFeature?.invoke(route)
            }
        },
    )
}

/**
 * Stateful entry: connects to the server, builds the repository over the
 * entity store and action queue, then renders the library. The connection
 * gate keeps the ViewModel constructor synchronous and fully testable.
 */
@Composable
fun SonarrSeriesLibrary(
    serverId: ServerId,
    deps: SonarrLibraryDeps,
) {
    var apis by remember(serverId) { mutableStateOf<SonarrSeriesLibraryApis?>(null) }
    LaunchedEffect(serverId) {
        apis = SonarrLibraryConnection(
            registry = deps.registry,
            secrets = deps.secrets,
            clientFactory = deps.clientFactory,
        ).connect(serverId)
    }
    val connected = apis
    if (connected == null) {
        ArrLceBox(state = LceState.Loading, modifier = Modifier.fillMaxSize()) {}
        return
    }
    val repository = remember(serverId, connected) {
        SonarrSeriesRepository(
            serverId = serverId,
            store = deps.entityStore,
            seriesApi = connected.seriesApi,
            seriesEditorApi = connected.seriesEditorApi,
            seasonPassApi = connected.seasonPassApi,
            qualityProfileApi = connected.qualityProfiles,
            tagApi = connected.tags,
            commandRunner = connected.commandRunner,
            actionQueue = deps.actionQueue,
        )
    }
    val savedFilters = remember(serverId) { SavedSeriesFiltersStore(deps.entityStore, serverId) }
    val vm: SeriesLibraryViewModel = viewModel(
        key = "sonarr-series-library-" + serverId.value,
        factory = viewModelFactory {
            initializer { SeriesLibraryViewModel(repository, savedFilters) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current
    SeriesLibraryContent(
        state = state,
        baseUrl = connected.baseUrl,
        imageLoader = rememberImageLoaderOf(connected),
        onRefresh = vm::refresh,
        onSetView = vm::setView,
        onSetSort = vm::setSort,
        onToggleSortDescending = vm::toggleSortDescending,
        onLoadMore = vm::loadMore,
        onFilterValuePicked = vm::setFilterValue,
        onClearFilters = vm::clearFilters,
        onApplySavedFilter = vm::applySavedFilter,
        onDeleteSavedFilter = vm::deleteSavedFilter,
        onSaveFilter = vm::saveFilter,
        onEnterSelectMode = vm::enterSelectMode,
        onExitSelectMode = vm::exitSelectMode,
        onToggleSelected = vm::toggleSelected,
        onSelectAllPresented = vm::selectAllPresented,
        onOpenSeries = remember(navigator, serverId) { { seriesId: Int ->
            // Detail is a built-in shell route owned by a later task; the
            // library only navigates to it.
            navigator.open(ArrRoute.Detail(serverId, AppType.SONARR, seriesId.toString()))
        } },
        onOpenEditor = vm::openEditor,
        onCloseEditor = vm::closeEditor,
        onUpdateEditorForm = remember(vm) { { form -> vm.updateEditor { form } } },
        onSubmitEditor = vm::submitEditor,
        onOpenSeasonPass = vm::openSeasonPass,
        onCloseSeasonPass = vm::closeSeasonPass,
        onUpdateSeasonPassForm = remember(vm) { { form -> vm.updateSeasonPass { form } } },
        onSubmitSeasonPass = vm::submitSeasonPass,
        onRequestDelete = vm::requestDelete,
        onDismissDelete = vm::dismissDelete,
        onConfirmDelete = vm::confirmDelete,
        onRefreshMetadata = vm::refreshMetadata,
        onScanDisk = vm::scanDisk,
        onConsumeMessage = vm::consumeMessage,
    )
}

/** Builds the authenticated loader once per connection, after the gate opens. */
@Composable
private fun rememberImageLoaderOf(apis: SonarrSeriesLibraryApis): ImageLoader? {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(apis) { apis.imageLoader(context) }
}
