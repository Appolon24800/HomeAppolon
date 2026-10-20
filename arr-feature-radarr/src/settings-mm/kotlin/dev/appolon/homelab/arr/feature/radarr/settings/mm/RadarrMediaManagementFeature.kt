package dev.appolon.homelab.arr.feature.radarr.settings.mm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.mm.ui.RadarrMediaManagementDestination
import dev.appolon.homelab.arr.feature.radarr.settings.mm.ui.RadarrNamingDestination
import dev.appolon.homelab.arr.feature.radarr.settings.mm.ui.RadarrRootFoldersDestination
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Everything the arr shell needs from the Radarr media management feature:
 * nested route patterns, open helpers, and the slot composable for
 * [ArrShellContent]. The integration layer calls [register] once before
 * composing the shell.
 */
object RadarrMediaManagementFeature {
    /** Shared with the other Radarr screen groups; patterns are settings-specific. */
    const val ROUTE_OWNER = "radarr"

    /** Import and file management toggles plus chmod/chown fields. */
    const val MEDIA_MANAGEMENT_PATTERN = "settings/mediamanagement/{serverId}"

    /** Movie and folder file name token builders with live preview. */
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
 * built by the composition root from [RadarrSettingsMmConnection].
 */
class RadarrSettingsMmClients(
    val store: EntityStore,
    val actionQueue: ActionQueue,
    val apis: RadarrSettingsMmApis,
)

/**
 * Shell content for this feature's nested routes. Settings screens do not
 * subscribe to live events: they refresh through the TTL and on
 * pull-to-refresh, so no refresh signals are plumbed here.
 */
fun radarrSettingsMmShellContent(
    clients: (ServerId) -> RadarrSettingsMmClients,
    serverNames: (ServerId) -> String? = { null },
): ArrShellContent = ArrShellContent(
    feature = { route ->
        RadarrMediaManagementFeatureSlot(
            route = route,
            clients = clients,
            serverNames = serverNames,
        )
    },
)

/**
 * Feature-slot entry: renders this feature's nested routes and an empty
 * state for anything else, mirroring the activity feature's slot so a
 * partial integration never shows a blank screen.
 */
@Composable
fun RadarrMediaManagementFeatureSlot(
    route: ArrRoute.Feature,
    clients: (ServerId) -> RadarrSettingsMmClients,
    serverNames: (ServerId) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    if (route.owner != RadarrMediaManagementFeature.ROUTE_OWNER) {
        ForeignOwnerPlaceholder(route.owner, modifier)
        return
    }
    val segments = route.path.split('/')
    val navigator = LocalArrShellNavigator.current
    val mediaManagementMatch =
        matchRoutePattern(RadarrMediaManagementFeature.MEDIA_MANAGEMENT_PATTERN, segments)
    val namingMatch = matchRoutePattern(RadarrMediaManagementFeature.NAMING_PATTERN, segments)
    val rootFoldersMatch =
        matchRoutePattern(RadarrMediaManagementFeature.ROOT_FOLDERS_PATTERN, segments)
    when {
        mediaManagementMatch != null -> {
            val serverId = ServerId(mediaManagementMatch.getValue("serverId"))
            RadarrMediaManagementDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                onOpenNaming = remember(navigator, serverId) { {
                    RadarrMediaManagementFeature.openNaming(navigator, serverId)
                } },
                onOpenRootFolders = remember(navigator, serverId) { {
                    RadarrMediaManagementFeature.openRootFolders(navigator, serverId)
                } },
                modifier = modifier,
            )
        }

        namingMatch != null -> {
            val serverId = ServerId(namingMatch.getValue("serverId"))
            RadarrNamingDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                modifier = modifier,
            )
        }

        rootFoldersMatch != null -> {
            val serverId = ServerId(rootFoldersMatch.getValue("serverId"))
            RadarrRootFoldersDestination(
                serverId = serverId,
                clients = clients(serverId),
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
