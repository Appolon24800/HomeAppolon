package dev.appolon.homelab.arr.feature.radarr.queue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.appolon.homelab.arr.api.radarr.FileSystemApi
import dev.appolon.homelab.arr.api.radarr.ManualImportApi
import dev.appolon.homelab.arr.api.radarr.QueueActionApi
import dev.appolon.homelab.arr.api.radarr.QueueApi
import dev.appolon.homelab.arr.api.radarr.QueueDetailsApi
import dev.appolon.homelab.arr.api.radarr.QueueStatusApi
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.feature.radarr.queue.data.QueueCommandRunner
import dev.appolon.homelab.arr.feature.radarr.queue.ui.RadarrManualImportDestination
import dev.appolon.homelab.arr.feature.radarr.queue.ui.RadarrQueueDestination
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Everything one server's queue screens need: the offline store and action
 * queue from the data graph, the Retrofit interfaces built by the
 * composition root from the [dev.appolon.homelab.arr.api.radarr.RadarrQueueApis]
 * factories, plus the raw-POST command runner the typed Command model cannot
 * express. Kept as one bundle so destinations take a single dependency.
 */
class RadarrQueueClients(
    val store: EntityStore,
    val actionQueue: ActionQueue,
    val queueApi: QueueApi,
    val queueActionApi: QueueActionApi,
    val queueDetailsApi: QueueDetailsApi,
    val queueStatusApi: QueueStatusApi,
    val manualImportApi: ManualImportApi,
    val fileSystemApi: FileSystemApi,
    val commandRunner: QueueCommandRunner,
)

/**
 * Everything the arr shell needs from the Radarr queue feature: nested route
 * patterns, open helpers, and the slot composables for [ArrShellContent].
 * The integration layer calls [register] once before composing the shell.
 */
object RadarrQueueFeature {
    /** Shared with the other Radarr screen groups; patterns are queue-specific so they cannot collide. */
    const val ROUTE_OWNER = "radarr"

    /** Interactive folder-mode manual import, started from the queue toolbar. */
    const val MANUAL_IMPORT_PATTERN = "queue/manual-import/{serverId}"

    /** Manual import tied to one queue item's download. */
    const val MANUAL_IMPORT_DOWNLOAD_PATTERN = "queue/manual-import/{serverId}/{downloadId}"

    val PATTERNS = listOf(MANUAL_IMPORT_PATTERN, MANUAL_IMPORT_DOWNLOAD_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun manualImportRoute(serverId: ServerId, downloadId: String?): ArrRoute.Feature? {
        val path = if (downloadId == null) {
            fillPattern(MANUAL_IMPORT_PATTERN, mapOf("serverId" to serverId.value))
        } else {
            fillPattern(
                MANUAL_IMPORT_DOWNLOAD_PATTERN,
                mapOf("serverId" to serverId.value, "downloadId" to downloadId),
            )
        } ?: return null
        return ArrRoute.Feature(ROUTE_OWNER, path)
    }

    fun openManualImport(navigator: ArrShellNavigator, serverId: ServerId, downloadId: String?) {
        manualImportRoute(serverId, downloadId)?.let(navigator::open)
    }
}

/**
 * Shell content for the built-in queue slot plus this feature's nested
 * routes. [onOverallProgress] feeds the shell's server switcher badge slot:
 * the composition root passes a callback through from wherever the badge is
 * rendered, and receives the bytes-weighted queue completion per server.
 */
fun radarrQueueShellContent(
    clients: (ServerId) -> RadarrQueueClients,
    refreshSignals: Flow<ServerId> = emptyFlow(),
    serverNames: (ServerId) -> String? = { null },
    onOverallProgress: (ServerId, Float?) -> Unit = { _, _ -> },
): ArrShellContent = ArrShellContent(
    queue = { route ->
        RadarrQueueDestination(
            route = route,
            clients = clients,
            refreshSignals = refreshSignals,
            serverNames = serverNames,
            onOverallProgress = onOverallProgress,
        )
    },
    feature = { route ->
        RadarrQueueFeatureSlot(
            route = route,
            clients = clients,
            serverNames = serverNames,
        )
    },
)

/**
 * Feature-slot entry: renders this feature's nested routes and an empty state
 * for anything else, mirroring the servers feature's slot so a partial
 * integration never shows a blank screen. The download-scoped route loads
 * its items by download id alone; Radarr resolves the download's path.
 */
@Composable
fun RadarrQueueFeatureSlot(
    route: ArrRoute.Feature,
    clients: (ServerId) -> RadarrQueueClients,
    serverNames: (ServerId) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    if (route.owner != RadarrQueueFeature.ROUTE_OWNER) {
        ForeignOwnerPlaceholder(route.owner, modifier)
        return
    }
    val segments = route.path.split('/')
    val navigator = LocalArrShellNavigator.current
    val downloadMatch = matchRoutePattern(RadarrQueueFeature.MANUAL_IMPORT_DOWNLOAD_PATTERN, segments)
    val folderMatch = matchRoutePattern(RadarrQueueFeature.MANUAL_IMPORT_PATTERN, segments)
    when {
        downloadMatch != null -> {
            val serverId = ServerId(downloadMatch.getValue("serverId"))
            RadarrManualImportDestination(
                serverId = serverId,
                clients = clients(serverId),
                downloadId = downloadMatch.getValue("downloadId"),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                modifier = modifier,
            )
        }

        folderMatch != null -> {
            val serverId = ServerId(folderMatch.getValue("serverId"))
            RadarrManualImportDestination(
                serverId = serverId,
                clients = clients(serverId),
                downloadId = null,
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                modifier = modifier,
            )
        }

        else -> ForeignOwnerPlaceholder(route.owner, modifier)
    }
}

@Composable
private fun ForeignOwnerPlaceholder(owner: String, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_title),
        message = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_message, owner),
        modifier = modifier,
    )
}
