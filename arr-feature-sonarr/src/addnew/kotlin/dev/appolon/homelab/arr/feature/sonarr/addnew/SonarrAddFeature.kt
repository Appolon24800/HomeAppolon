package dev.appolon.homelab.arr.feature.sonarr.addnew

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
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.sonarr.addnew.data.SonarrAddRepository
import dev.appolon.homelab.arr.feature.sonarr.addnew.ui.AddSeriesContent
import dev.appolon.homelab.arr.feature.sonarr.addnew.vm.AddSeriesViewModel
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent

/**
 * Route registration for the add-new screen. The shell's built-in
 * `server/{serverId}/sonarr/add` pattern renders this feature through
 * [ArrRoute.Add]; this registration adds the feature-owned alias
 * `add/{serverId}` so other Sonarr screens and deep links can open the add
 * flow without duplicating shell knowledge.
 */
object SonarrAddRoutes {
    const val OWNER = "sonarr"
    const val ADD_PATTERN = "add/{serverId}"

    /** Registers this feature's nested routes; false when already present. */
    fun register(registry: ArrRouteRegistry): Boolean =
        registry.register(OWNER, ADD_PATTERN)

    /** Encoded nested route for one server's add-new screen. */
    fun addRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(ADD_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { path -> ArrRoute.Feature(OWNER, path) }

    /** Extracts the server a feature route points at, or null for other owners. */
    fun serverIdOf(route: ArrRoute.Feature): ServerId? {
        if (route.owner != OWNER) return null
        // Nested paths travel percent-encoded; decode before matching so the
        // extracted id equals the stored ServerId.
        val segments = route.path.split('/').map(::decodeSegment)
        val params = matchRoutePattern(ADD_PATTERN, segments) ?: return null
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

/** Everything the add-new screen needs from the app's singletons. */
class SonarrAddDeps(
    val registry: ServerRegistry,
    val secrets: ArrSecrets,
    val entityStore: EntityStore,
    val actionQueue: ActionQueue,
    val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {
    companion object {
        fun from(graph: ArrDataGraph) = SonarrAddDeps(
            registry = graph.registry,
            secrets = graph.secrets,
            entityStore = graph.entityStore,
            actionQueue = graph.actionQueue,
        )
    }
}

/**
 * Fills the shell's add slot for Sonarr and routes this feature's nested add
 * alias to the same screen. Other apps fall through to the previous slot, so
 * sibling feature modules can wrap the same content object.
 */
fun ArrShellContent.withSonarrAddNew(
    deps: SonarrAddDeps,
    registry: ArrRouteRegistry,
): ArrShellContent {
    SonarrAddRoutes.register(registry)
    val previousAdd = add
    val previousFeature = feature
    // ArrShellContent has no copy(); rebuild it slot by slot so sibling
    // features can wrap the same content object.
    return ArrShellContent(
        serverList = serverList,
        serverHome = serverHome,
        library = library,
        detail = detail,
        add = { route ->
            if (route.app == AppType.SONARR) {
                SonarrAddNew(serverId = route.serverId, deps = deps)
            } else {
                previousAdd?.invoke(route)
            }
        },
        queue = queue,
        calendar = calendar,
        settings = settings,
        feature = { route ->
            val serverId = SonarrAddRoutes.serverIdOf(route)
            if (serverId != null) {
                SonarrAddNew(serverId = serverId, deps = deps)
            } else {
                previousFeature?.invoke(route)
            }
        },
    )
}

/**
 * Stateful entry: connects to the server, builds the repository over the
 * entity store and action queue, then renders the add-new screen. The
 * connection gate keeps the ViewModel constructor synchronous and testable.
 */
@Composable
fun SonarrAddNew(
    serverId: ServerId,
    deps: SonarrAddDeps,
) {
    var apis by remember(serverId) { mutableStateOf<SonarrAddApis?>(null) }
    LaunchedEffect(serverId) {
        apis = SonarrAddConnection(
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
        SonarrAddRepository(
            serverId = serverId,
            store = deps.entityStore,
            seriesApi = connected.seriesApi,
            lookupRunner = connected.lookupRunner,
            rootFolderApi = connected.rootFolders,
            qualityProfileApi = connected.qualityProfiles,
            languageProfileApi = connected.languageProfiles,
            tagApi = connected.tags,
            importListApi = connected.importListApi,
            importListExclusionApi = connected.importListExclusionApi,
            actionQueue = deps.actionQueue,
        )
    }
    val vm: AddSeriesViewModel = viewModel(
        key = "sonarr-add-new-" + serverId.value,
        factory = viewModelFactory {
            initializer { AddSeriesViewModel(repository) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    AddSeriesContent(
        state = state,
        imageLoader = rememberImageLoaderOf(connected),
        onQueryChange = vm::onQueryChange,
        onSearch = vm::search,
        onYearPicked = vm::setYearFilter,
        onRefresh = {
            vm.refreshDefaults()
            vm.search()
        },
        onRefreshLists = vm::refreshLists,
        onOpenAddForm = vm::openAddForm,
        onCloseAddForm = vm::closeAddForm,
        onUpdateAddForm = { form -> vm.updateAddForm { form } },
        onSetMonitor = vm::setMonitorStrategy,
        onToggleSeason = vm::toggleSeason,
        onToggleTag = vm::toggleTag,
        onAdd = vm::submitAdd,
        onSyncList = vm::syncImportList,
        onOpenExclusionDraft = vm::openExclusionDraft,
        onCloseExclusionDraft = vm::closeExclusionDraft,
        onUpdateExclusionDraft = { draft -> vm.updateExclusionDraft { draft } },
        onSubmitExclusion = vm::submitExclusion,
        onDeleteExclusion = vm::deleteExclusion,
        onConsumeMessage = vm::consumeMessage,
    )
}

/** Builds the authenticated loader once per connection, after the gate opens. */
@Composable
private fun rememberImageLoaderOf(apis: SonarrAddApis): ImageLoader? {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(apis) { apis.imageLoader(context) }
}
