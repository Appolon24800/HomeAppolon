package dev.appolon.homelab.arr.feature.lidarr.queue

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.appolon.homelab.arr.api.lidarr.FileSystemApi
import dev.appolon.homelab.arr.api.lidarr.ManualImportApi
import dev.appolon.homelab.arr.api.lidarr.QueueActionApi
import dev.appolon.homelab.arr.api.lidarr.QueueApi
import dev.appolon.homelab.arr.api.lidarr.QueueDetailsApi
import dev.appolon.homelab.arr.api.lidarr.QueueStatusApi
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.feature.lidarr.queue.data.QueueCommandRunner
import dev.appolon.homelab.arr.feature.lidarr.queue.ui.LidarrManualImportDestination
import dev.appolon.homelab.arr.feature.lidarr.queue.ui.LidarrQueueDestination
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
 * composition root from the [dev.appolon.homelab.arr.api.lidarr.LidarrQueueApis]
 * factories, plus the raw-POST command runner the typed Command model cannot
 * express. Kept as one bundle so destinations take a single dependency.
 */
class LidarrQueueClients(
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
 * Everything the arr shell needs from the Lidarr queue feature: nested route
 * patterns, open helpers, and the slot composables for [ArrShellContent].
 * The integration layer calls [register] once before composing the shell.
 */
object LidarrQueueFeature {
    /**
     * This group's own owner string, matching the per-group convention of the
     * other Lidarr features; the queue's patterns are queue-specific so they
     * cannot collide with the library's or add-new's.
     */
    const val ROUTE_OWNER = "lidarr-queue"

    /** Interactive folder-mode manual import, started from the queue toolbar. */
    const val MANUAL_IMPORT_PATTERN = "queue/manual-import/{serverId}"

    /** Manual import tied to one queue item's download. */
    const val MANUAL_IMPORT_DOWNLOAD_PATTERN = "queue/manual-import/{serverId}/{downloadId}"

    val PATTERNS = listOf(MANUAL_IMPORT_PATTERN, MANUAL_IMPORT_DOWNLOAD_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun manualImportRoute(serverId: ServerId, downloadId: String?): ArrRoute.Feature? {
        // Blank download ids degrade to folder mode rather than dead-ending.
        val target = downloadId?.takeIf { it.isNotEmpty() }
        val path = if (target == null) {
            fillPattern(MANUAL_IMPORT_PATTERN, mapOf("serverId" to serverId.value))
        } else {
            fillPattern(
                MANUAL_IMPORT_DOWNLOAD_PATTERN,
                mapOf("serverId" to serverId.value, "downloadId" to target),
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
fun lidarrQueueShellContent(
    clients: (ServerId) -> LidarrQueueClients,
    refreshSignals: Flow<ServerId> = emptyFlow(),
    serverNames: (ServerId) -> String? = { null },
    onOverallProgress: (ServerId, Float?) -> Unit = { _, _ -> },
): ArrShellContent = ArrShellContent(
    queue = { route ->
        LidarrQueueDestination(
            route = route,
            clients = clients,
            refreshSignals = refreshSignals,
            serverNames = serverNames,
            onOverallProgress = onOverallProgress,
        )
    },
    feature = { route ->
        LidarrQueueFeatureSlot(
            route = route,
            clients = clients,
            serverNames = serverNames,
        )
    },
)

/**
 * Feature-slot entry: renders this feature's nested routes and falls through
 * to [fallback] for anything else (sibling features chain their slots this
 * way), showing the shared unavailable state when no fallback is given.
 * The download-scoped route loads its items by download id alone; Lidarr
 * resolves the download's path.
 */
@Composable
fun LidarrQueueFeatureSlot(
    route: ArrRoute.Feature,
    clients: (ServerId) -> LidarrQueueClients,
    serverNames: (ServerId) -> String? = { null },
    modifier: Modifier = Modifier,
    fallback: (@Composable (ArrRoute.Feature) -> Unit)? = null,
) {
    val segments = route.path.split('/').map(::decodeRouteSegment)
    val navigator = LocalArrShellNavigator.current
    val downloadMatch = matchRoutePattern(LidarrQueueFeature.MANUAL_IMPORT_DOWNLOAD_PATTERN, segments)
    val folderMatch = matchRoutePattern(LidarrQueueFeature.MANUAL_IMPORT_PATTERN, segments)
    when {
        downloadMatch != null -> {
            val serverId = ServerId(downloadMatch.getValue("serverId"))
            LidarrManualImportDestination(
                serverId = serverId,
                clients = clients(serverId),
                downloadId = downloadMatch.getValue("downloadId"),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                modifier = modifier,
            )
        }

        folderMatch != null -> {
            val serverId = ServerId(folderMatch.getValue("serverId"))
            LidarrManualImportDestination(
                serverId = serverId,
                clients = clients(serverId),
                downloadId = null,
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                modifier = modifier,
            )
        }

        else -> if (fallback != null) {
            fallback(route)
        } else {
            LidarrRouteUnavailable(route.owner, modifier)
        }
    }
}

/** Shared "screen not available" pane for feature routes this module cannot render. */
@Composable
internal fun LidarrRouteUnavailable(owner: String, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_title),
        message = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_message, owner),
        modifier = modifier,
    )
}

/**
 * Strict RFC 3986 percent decoding for one path segment. Feature paths
 * travel encoded (that is what [fillPattern] produces), so parameter values
 * extracted from them must be decoded before use; malformed escapes stay
 * literal, mirroring the library's route helpers.
 */
internal fun decodeRouteSegment(raw: String): String {
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
