package dev.appolon.homelab.arr.feature.lidarr.settings.mm

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.ui.LidarrMediaManagementDestination
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.ui.LidarrNamingDestination
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.ui.LidarrRootFoldersDestination
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Everything the arr shell needs from the Lidarr media management
 * feature: nested route patterns, open helpers, and the slot composable
 * for [ArrShellContent]. The integration layer calls [register] once
 * before composing the shell.
 */
object LidarrMediaManagementFeature {
    /** Shared with the other Lidarr screen groups; patterns are settings-specific. */
    const val ROUTE_OWNER = "lidarr"

    /** Import and file management toggles plus chmod/chown fields. */
    const val MEDIA_MANAGEMENT_PATTERN = "settings/mediamanagement/{serverId}"

    /** Track and folder file name token builders with live preview. */
    const val NAMING_PATTERN = "settings/mediamanagement/naming/{serverId}"

    /** Root folders with free space bars and the path browser. */
    const val ROOT_FOLDERS_PATTERN = "settings/mediamanagement/rootfolders/{serverId}"

    val PATTERNS = listOf(MEDIA_MANAGEMENT_PATTERN, NAMING_PATTERN, ROOT_FOLDERS_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun mediaManagementRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(MEDIA_MANAGEMENT_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun namingRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(NAMING_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun rootFoldersRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(ROOT_FOLDERS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun openMediaManagement(navigator: ArrShellNavigator, serverId: ServerId) {
        mediaManagementRoute(serverId)?.let(navigator::open)
    }

    fun openNaming(navigator: ArrShellNavigator, serverId: ServerId) {
        namingRoute(serverId)?.let(navigator::open)
    }

    fun openRootFolders(navigator: ArrShellNavigator, serverId: ServerId) {
        rootFoldersRoute(serverId)?.let(navigator::open)
    }
}

/**
 * Everything one server's media management screens need: the offline
 * store and action queue from the data graph plus the Retrofit interfaces
 * built by the composition root from [LidarrSettingsMmConnection].
 */
class LidarrSettingsMmClients(
    val store: EntityStore,
    val actionQueue: ActionQueue,
    val apis: LidarrSettingsMmApis,
)

/**
 * Shell content for this feature's nested routes. Settings screens do not
 * subscribe to live events: they refresh through the TTL and on
 * pull-to-refresh, so no refresh signals are plumbed here.
 */
fun lidarrSettingsMmShellContent(
    clients: (ServerId) -> LidarrSettingsMmClients,
    serverNames: (ServerId) -> String? = { null },
): ArrShellContent = ArrShellContent(
    feature = { route ->
        LidarrMediaManagementFeatureSlot(
            route = route,
            clients = clients,
            serverNames = serverNames,
        )
    },
)

/**
 * Feature-slot entry: renders this feature's nested routes and an empty
 * state for anything else, mirroring the Radarr and Sonarr settings slots
 * so a partial integration never shows a blank screen.
 */
@Composable
fun LidarrMediaManagementFeatureSlot(
    route: ArrRoute.Feature,
    clients: (ServerId) -> LidarrSettingsMmClients,
    serverNames: (ServerId) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    if (route.owner != LidarrMediaManagementFeature.ROUTE_OWNER) {
        ForeignOwnerPlaceholder(route.owner, modifier)
        return
    }
    val segments = route.path.split('/')
    val navigator = LocalArrShellNavigator.current
    val mediaManagementMatch =
        matchRoutePattern(LidarrMediaManagementFeature.MEDIA_MANAGEMENT_PATTERN, segments)
    val namingMatch = matchRoutePattern(LidarrMediaManagementFeature.NAMING_PATTERN, segments)
    val rootFoldersMatch =
        matchRoutePattern(LidarrMediaManagementFeature.ROOT_FOLDERS_PATTERN, segments)
    when {
        mediaManagementMatch != null -> {
            val serverId = ServerId(mediaManagementMatch.getValue("serverId"))
            LidarrMediaManagementDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                onOpenNaming = {
                    LidarrMediaManagementFeature.openNaming(navigator, serverId)
                },
                onOpenRootFolders = {
                    LidarrMediaManagementFeature.openRootFolders(navigator, serverId)
                },
                modifier = modifier,
            )
        }

        namingMatch != null -> {
            val serverId = ServerId(namingMatch.getValue("serverId"))
            LidarrNamingDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                modifier = modifier,
            )
        }

        rootFoldersMatch != null -> {
            val serverId = ServerId(rootFoldersMatch.getValue("serverId"))
            LidarrRootFoldersDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
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
