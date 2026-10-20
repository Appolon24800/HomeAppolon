package dev.appolon.homelab.arr.feature.radarr.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.appolon.homelab.arr.api.radarr.BlocklistApi
import dev.appolon.homelab.arr.api.radarr.HistoryApi
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.feature.radarr.activity.ui.RadarrBlocklistDestination
import dev.appolon.homelab.arr.feature.radarr.activity.ui.RadarrHistoryDestination
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
 * Everything one server's activity screens need: the offline store and
 * action queue from the data graph plus the Retrofit interfaces built by the
 * composition root from the [dev.appolon.homelab.arr.api.radarr.RadarrQueueApis]
 * factories. Kept as one bundle so destinations take a single dependency.
 */
class RadarrActivityClients(
    val store: EntityStore,
    val actionQueue: ActionQueue,
    val historyApi: HistoryApi,
    val blocklistApi: BlocklistApi,
)

/**
 * Everything the arr shell needs from the Radarr activity feature: nested
 * route patterns, open helpers, and the slot composable for
 * [ArrShellContent]. The integration layer calls [register] once before
 * composing the shell.
 */
object RadarrActivityFeature {
    /** Shared with the other Radarr screen groups; patterns are activity-specific so they cannot collide. */
    const val ROUTE_OWNER = "radarr"

    /** The history feed with filters and per-event detail. */
    const val HISTORY_PATTERN = "activity/history/{serverId}"

    /** The blocklist with per-entry reasons and removal. */
    const val BLOCKLIST_PATTERN = "activity/blocklist/{serverId}"

    val PATTERNS = listOf(HISTORY_PATTERN, BLOCKLIST_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun historyRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(HISTORY_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun blocklistRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(BLOCKLIST_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun openHistory(navigator: ArrShellNavigator, serverId: ServerId) {
        historyRoute(serverId)?.let(navigator::open)
    }

    fun openBlocklist(navigator: ArrShellNavigator, serverId: ServerId) {
        blocklistRoute(serverId)?.let(navigator::open)
    }
}

/**
 * Shell content for this feature's nested routes. [refreshSignals] carries
 * the live queue events per server; both screens debounce it into a refresh.
 */
fun radarrActivityShellContent(
    clients: (ServerId) -> RadarrActivityClients,
    refreshSignals: Flow<ServerId> = emptyFlow(),
    serverNames: (ServerId) -> String? = { null },
): ArrShellContent = ArrShellContent(
    feature = { route ->
        RadarrActivityFeatureSlot(
            route = route,
            clients = clients,
            refreshSignals = refreshSignals,
            serverNames = serverNames,
        )
    },
)

/**
 * Feature-slot entry: renders this feature's nested routes and an empty
 * state for anything else, mirroring the queue feature's slot so a partial
 * integration never shows a blank screen.
 */
@Composable
fun RadarrActivityFeatureSlot(
    route: ArrRoute.Feature,
    clients: (ServerId) -> RadarrActivityClients,
    refreshSignals: Flow<ServerId> = emptyFlow(),
    serverNames: (ServerId) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    if (route.owner != RadarrActivityFeature.ROUTE_OWNER) {
        ForeignOwnerPlaceholder(route.owner, modifier)
        return
    }
    val segments = route.path.split('/')
    val navigator = LocalArrShellNavigator.current
    val historyMatch = matchRoutePattern(RadarrActivityFeature.HISTORY_PATTERN, segments)
    val blocklistMatch = matchRoutePattern(RadarrActivityFeature.BLOCKLIST_PATTERN, segments)
    when {
        historyMatch != null -> {
            val serverId = ServerId(historyMatch.getValue("serverId"))
            RadarrHistoryDestination(
                serverId = serverId,
                clients = clients(serverId),
                refreshSignals = refreshSignals,
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                modifier = modifier,
            )
        }

        blocklistMatch != null -> {
            val serverId = ServerId(blocklistMatch.getValue("serverId"))
            RadarrBlocklistDestination(
                serverId = serverId,
                clients = clients(serverId),
                refreshSignals = refreshSignals,
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
