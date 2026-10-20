package dev.appolon.homelab.arr.feature.lidarr.queue

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
import dev.appolon.homelab.arr.api.lidarr.LidarrQueueApis
import dev.appolon.homelab.arr.api.lidarr.LidarrSystemApis
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.lidarr.queue.data.QueueCommandBody
import dev.appolon.homelab.arr.feature.lidarr.queue.data.QueueCommandRunner
import dev.appolon.homelab.arr.feature.lidarr.queue.data.LidarrManualImportRepository
import dev.appolon.homelab.arr.feature.lidarr.queue.data.LidarrQueueRepository
import dev.appolon.homelab.arr.feature.lidarr.queue.ui.LidarrManualImportScreen
import dev.appolon.homelab.arr.feature.lidarr.queue.ui.LidarrQueueScreen
import dev.appolon.homelab.arr.feature.lidarr.queue.vm.LidarrManualImportViewModel
import dev.appolon.homelab.arr.feature.lidarr.queue.vm.LidarrQueueViewModel
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrCommandRunner
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Everything the queue screens need from the app's singletons. */
class LidarrQueueDeps(
    val registry: ServerRegistry,
    val secrets: ArrSecrets,
    val entityStore: EntityStore,
    val actionQueue: ActionQueue,
    val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {
    companion object {
        fun from(graph: ArrDataGraph) = LidarrQueueDeps(
            registry = graph.registry,
            secrets = graph.secrets,
            entityStore = graph.entityStore,
            actionQueue = graph.actionQueue,
        )
    }
}

/**
 * Everything the queue screens need to talk to one Lidarr server, built on
 * the committed client stack: [DefaultArrClientFactory] honors proxy, basic
 * auth, and trust, and one Retrofit instance per server serves the queue
 * endpoints, the filesystem browser, and raw command posts alike.
 */
class LidarrQueueConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val store: EntityStore,
    private val actionQueue: ActionQueue,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): LidarrQueueClients? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return LidarrQueueClients(
            store = store,
            actionQueue = actionQueue,
            queueApi = LidarrQueueApis.queue(retrofit),
            queueActionApi = LidarrQueueApis.queueAction(retrofit),
            queueDetailsApi = LidarrQueueApis.queueDetails(retrofit),
            queueStatusApi = LidarrQueueApis.queueStatus(retrofit),
            manualImportApi = LidarrQueueApis.manualImport(retrofit),
            fileSystemApi = LidarrSystemApis.fileSystem(retrofit),
            commandRunner = OkHttpLidarrQueueCommandRunner(client, baseUrl),
        )
    }

    private fun endpointOf(server: ArrServer): ServerEndpoint? = try {
        ServerEndpoint(
            serverId = server.id,
            baseUrl = server.baseUrl,
            apiKey = secrets.decrypt(server.apiKeyEncrypted),
            basicAuthUser = server.basicAuthUser,
            basicAuthPassword = server.basicAuthPasswordEncrypted?.let { secrets.decrypt(it) },
            proxy = server.proxy,
            trust = server.trust ?: TrustSpec.System,
        )
    } catch (t: Throwable) {
        null
    }
}

/**
 * Fills the shell's queue slot for Lidarr and routes this feature's nested
 * manual import routes, leaving every other app's slots untouched so sibling
 * feature modules can wrap the same content object. [refreshSignals] carries
 * `ArrEvent.QueueChanged` per server from the live connection manager, and
 * [onOverallProgress] feeds the server switcher's badge slot.
 */
fun ArrShellContent.withLidarrQueue(
    deps: LidarrQueueDeps,
    registry: ArrRouteRegistry,
    refreshSignals: Flow<ServerId> = emptyFlow(),
    serverNames: (ServerId) -> String? = { null },
    onOverallProgress: (ServerId, Float?) -> Unit = { _, _ -> },
): ArrShellContent {
    LidarrQueueFeature.register(registry)
    val previousQueue = queue
    val previousFeature = feature
    // ArrShellContent has no copy(); rebuild it slot by slot so sibling
    // features can wrap the same content object.
    return ArrShellContent(
        serverList = serverList,
        serverHome = serverHome,
        library = library,
        detail = detail,
        add = add,
        queue = { route ->
            if (route.app == AppType.LIDARR) {
                LidarrQueue(
                    serverId = route.serverId,
                    deps = deps,
                    refreshSignals = refreshSignals,
                    serverName = serverNames(route.serverId),
                    onOverallProgress = onOverallProgress,
                )
            } else {
                previousQueue?.invoke(route)
            }
        },
        calendar = calendar,
        settings = settings,
        feature = { route ->
            val target = lidarrQueueRouteTarget(route)
            if (target != null) {
                LidarrManualImport(
                    serverId = target.first,
                    deps = deps,
                    downloadId = target.second,
                    serverName = serverNames(target.first),
                )
            } else {
                previousFeature?.invoke(route)
            }
        },
    )
}

/** Server id and download id of a manual import route, or null for any other route. */
private fun lidarrQueueRouteTarget(route: ArrRoute.Feature): Pair<ServerId, String?>? {
    if (route.owner != LidarrQueueFeature.ROUTE_OWNER) return null
    val segments = route.path.split('/').map(::decodeRouteSegment)
    val downloadMatch = matchRoutePattern(LidarrQueueFeature.MANUAL_IMPORT_DOWNLOAD_PATTERN, segments)
    if (downloadMatch != null) {
        return ServerId(downloadMatch.getValue("serverId")) to downloadMatch.getValue("downloadId")
    }
    val folderMatch = matchRoutePattern(LidarrQueueFeature.MANUAL_IMPORT_PATTERN, segments)
    if (folderMatch != null) {
        return ServerId(folderMatch.getValue("serverId")) to null
    }
    return null
}

/**
 * Stateful queue entry: connects to the server, builds the repository over
 * the entity store and action queue, then renders the list. The connection
 * gate keeps the ViewModel constructor synchronous and fully testable.
 */
@Composable
fun LidarrQueue(
    serverId: ServerId,
    deps: LidarrQueueDeps,
    refreshSignals: Flow<ServerId> = emptyFlow(),
    serverName: String? = null,
    onOverallProgress: (ServerId, Float?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var clients by remember(serverId) { mutableStateOf<LidarrQueueClients?>(null) }
    LaunchedEffect(serverId) {
        clients = LidarrQueueConnection(
            registry = deps.registry,
            secrets = deps.secrets,
            store = deps.entityStore,
            actionQueue = deps.actionQueue,
            clientFactory = deps.clientFactory,
        ).connect(serverId)
    }
    val connected = clients
    if (connected == null) {
        ArrLceBox(state = LceState.Loading, modifier = modifier.fillMaxSize()) {}
        return
    }
    val repository = remember(serverId, connected) {
        LidarrQueueRepository(
            serverId = serverId,
            store = connected.store,
            queueDetailsApi = connected.queueDetailsApi,
            queueApi = connected.queueApi,
            queueActionApi = connected.queueActionApi,
            queueStatusApi = connected.queueStatusApi,
            commandRunner = connected.commandRunner,
            actionQueue = connected.actionQueue,
        )
    }
    val vm: LidarrQueueViewModel = viewModel(key = "lidarr-queue-" + serverId.value) {
        LidarrQueueViewModel(
            serverId = serverId,
            repository = repository,
            refreshSignals = refreshSignals,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val overall by vm.overallProgress.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    LaunchedEffect(serverId, overall) {
        onOverallProgress(serverId, overall)
    }

    LidarrQueueScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onRefresh = vm::refresh,
        onFilter = vm::setFilter,
        onRemove = vm::removeItem,
        onRetryImport = vm::retryImport,
        onGrab = vm::grab,
        onOpenManualImport = { downloadId ->
            LidarrQueueFeature.openManualImport(navigator, serverId, downloadId)
        },
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Stateful manual import entry for the feature slot: with a [downloadId] it
 * lists that download's files, otherwise it starts the interactive folder
 * browser at the server root.
 */
@Composable
fun LidarrManualImport(
    serverId: ServerId,
    deps: LidarrQueueDeps,
    downloadId: String?,
    serverName: String? = null,
    modifier: Modifier = Modifier,
) {
    var clients by remember(serverId) { mutableStateOf<LidarrQueueClients?>(null) }
    LaunchedEffect(serverId) {
        clients = LidarrQueueConnection(
            registry = deps.registry,
            secrets = deps.secrets,
            store = deps.entityStore,
            actionQueue = deps.actionQueue,
            clientFactory = deps.clientFactory,
        ).connect(serverId)
    }
    val connected = clients
    if (connected == null) {
        ArrLceBox(state = LceState.Loading, modifier = modifier.fillMaxSize()) {}
        return
    }
    val repository = remember(serverId, connected) {
        LidarrManualImportRepository(
            serverId = serverId,
            manualImportApi = connected.manualImportApi,
            fileSystemCall = { path, includeFiles ->
                connected.fileSystemApi.getFileSystem(path = path, includeFiles = includeFiles)
            },
            actionQueue = connected.actionQueue,
        )
    }
    val vm: LidarrManualImportViewModel = viewModel(
        key = "lidarr-manual-import-" + serverId.value + "-" + (downloadId ?: "folder"),
    ) {
        LidarrManualImportViewModel(
            repository = repository,
            downloadId = downloadId,
            outputPath = null,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    LidarrManualImportScreen(
        state = state,
        serverName = serverName,
        onBack = { navigator.back() },
        onOpenFolder = { folder -> vm.browse(folder.path) },
        onScanFolder = vm::loadFolderItems,
        onToggle = vm::toggle,
        onSelectAll = vm::selectAll,
        onClearSelection = vm::clearSelection,
        onImport = vm::importSelected,
        onRetryItems = vm::loadFolderItems,
        onRetryFolders = vm::browse,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Runs queue commands over a raw POST instead of the Retrofit `CommandApi`:
 * command payloads put selection fields like `downloadId`, `path`, and
 * `albumIds` at the JSON root, a shape the committed `CommandResource`
 * DTO cannot express. The body is built here; the wire path, auth, and
 * prefix handling are [ArrCommandRunner].
 */
class OkHttpLidarrQueueCommandRunner(
    client: OkHttpClient,
    baseUrl: String,
) : QueueCommandRunner {

    private val runner = ArrCommandRunner(client, baseUrl)

    override suspend fun run(
        name: String,
        downloadId: String?,
        path: String?,
        albumIds: List<Int>?,
    ): ArrResult<Unit> {
        val body = QueueCommandBody(
            name = name,
            downloadId = downloadId,
            path = path,
            albumIds = albumIds,
        ).encode(ArrJson)
        return withContext(Dispatchers.IO) { runner.post("/api/v1/command", body) }
    }
}
