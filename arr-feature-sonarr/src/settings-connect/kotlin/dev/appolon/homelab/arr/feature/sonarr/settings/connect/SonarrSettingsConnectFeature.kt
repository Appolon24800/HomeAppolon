package dev.appolon.homelab.arr.feature.sonarr.settings.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui.SonarrDownloadClientEditorDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui.SonarrDownloadClientsDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui.SonarrImportListEditorDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui.SonarrImportListExclusionsDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui.SonarrImportListsDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui.SonarrIndexerEditorDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui.SonarrIndexersDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui.SonarrMetadataDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui.SonarrNotificationEditorDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui.SonarrNotificationsDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui.SonarrRemotePathMappingsDestination
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Everything the arr shell needs from the Sonarr connect settings group:
 * nested route patterns, open helpers, and the slot composable for
 * [ArrShellContent]. The integration layer calls [register] once before
 * composing the shell.
 */
object SonarrSettingsConnectFeature {
    /** Shared with the other Sonarr screen groups; patterns are settings-specific. */
    const val ROUTE_OWNER = "sonarr"

    /** Indexers list with test-all and indexer options. */
    const val INDEXERS_PATTERN = "settings/indexers/{serverId}"

    /** Schema-driven indexer builder; indexerId "new" starts at the definition picker. */
    const val INDEXER_EDITOR_PATTERN = "settings/indexers/{serverId}/{indexerId}"

    /** Download clients list with enable toggles and download client options. */
    const val DOWNLOAD_CLIENTS_PATTERN = "settings/downloadclients/{serverId}"

    /** Schema-driven download client builder; clientId "new" creates. */
    const val DOWNLOAD_CLIENT_EDITOR_PATTERN = "settings/downloadclients/{serverId}/{clientId}"

    /** Remote path mappings CRUD. */
    const val REMOTE_PATH_MAPPINGS_PATTERN = "settings/remotepathmappings/{serverId}"

    /** Import lists list with sync and test-all. */
    const val IMPORT_LISTS_PATTERN = "settings/importlists/{serverId}"

    /** Schema-driven import list builder; listId "new" creates. */
    const val IMPORT_LIST_EDITOR_PATTERN = "settings/importlists/{serverId}/{listId}"

    /** Import list exclusions CRUD. */
    const val IMPORT_LIST_EXCLUSIONS_PATTERN = "settings/importlistexclusions/{serverId}"

    /** Connect notifications list. */
    const val NOTIFICATIONS_PATTERN = "settings/notifications/{serverId}"

    /** Notification builder with the event matrix; notificationId "new" creates. */
    const val NOTIFICATION_EDITOR_PATTERN = "settings/notifications/{serverId}/{notificationId}"

    /** Metadata consumers. */
    const val METADATA_PATTERN = "settings/metadata/{serverId}"

    val PATTERNS = listOf(
        INDEXERS_PATTERN,
        INDEXER_EDITOR_PATTERN,
        DOWNLOAD_CLIENTS_PATTERN,
        DOWNLOAD_CLIENT_EDITOR_PATTERN,
        REMOTE_PATH_MAPPINGS_PATTERN,
        IMPORT_LISTS_PATTERN,
        IMPORT_LIST_EDITOR_PATTERN,
        IMPORT_LIST_EXCLUSIONS_PATTERN,
        NOTIFICATIONS_PATTERN,
        NOTIFICATION_EDITOR_PATTERN,
        METADATA_PATTERN,
    )

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun indexersRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(INDEXERS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun indexerEditorRoute(serverId: ServerId, indexerId: Int?): ArrRoute.Feature? =
        fillPattern(
            INDEXER_EDITOR_PATTERN,
            mapOf("serverId" to serverId.value, "indexerId" to (indexerId?.toString() ?: "new")),
        )?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun downloadClientsRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(DOWNLOAD_CLIENTS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun downloadClientEditorRoute(serverId: ServerId, clientId: Int?): ArrRoute.Feature? =
        fillPattern(
            DOWNLOAD_CLIENT_EDITOR_PATTERN,
            mapOf("serverId" to serverId.value, "clientId" to (clientId?.toString() ?: "new")),
        )?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun remotePathMappingsRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(REMOTE_PATH_MAPPINGS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun importListsRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(IMPORT_LISTS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun importListEditorRoute(serverId: ServerId, listId: Int?): ArrRoute.Feature? =
        fillPattern(
            IMPORT_LIST_EDITOR_PATTERN,
            mapOf("serverId" to serverId.value, "listId" to (listId?.toString() ?: "new")),
        )?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun importListExclusionsRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(IMPORT_LIST_EXCLUSIONS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun notificationsRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(NOTIFICATIONS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun notificationEditorRoute(serverId: ServerId, notificationId: Int?): ArrRoute.Feature? =
        fillPattern(
            NOTIFICATION_EDITOR_PATTERN,
            mapOf(
                "serverId" to serverId.value,
                "notificationId" to (notificationId?.toString() ?: "new"),
            ),
        )?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun metadataRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(METADATA_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun openIndexers(navigator: ArrShellNavigator, serverId: ServerId) {
        indexersRoute(serverId)?.let(navigator::open)
    }

    fun openIndexerEditor(navigator: ArrShellNavigator, serverId: ServerId, indexerId: Int?) {
        indexerEditorRoute(serverId, indexerId)?.let(navigator::open)
    }

    fun openDownloadClients(navigator: ArrShellNavigator, serverId: ServerId) {
        downloadClientsRoute(serverId)?.let(navigator::open)
    }

    fun openDownloadClientEditor(navigator: ArrShellNavigator, serverId: ServerId, clientId: Int?) {
        downloadClientEditorRoute(serverId, clientId)?.let(navigator::open)
    }

    fun openRemotePathMappings(navigator: ArrShellNavigator, serverId: ServerId) {
        remotePathMappingsRoute(serverId)?.let(navigator::open)
    }

    fun openImportLists(navigator: ArrShellNavigator, serverId: ServerId) {
        importListsRoute(serverId)?.let(navigator::open)
    }

    fun openImportListEditor(navigator: ArrShellNavigator, serverId: ServerId, listId: Int?) {
        importListEditorRoute(serverId, listId)?.let(navigator::open)
    }

    fun openImportListExclusions(navigator: ArrShellNavigator, serverId: ServerId) {
        importListExclusionsRoute(serverId)?.let(navigator::open)
    }

    fun openNotifications(navigator: ArrShellNavigator, serverId: ServerId) {
        notificationsRoute(serverId)?.let(navigator::open)
    }

    fun openNotificationEditor(navigator: ArrShellNavigator, serverId: ServerId, notificationId: Int?) {
        notificationEditorRoute(serverId, notificationId)?.let(navigator::open)
    }

    fun openMetadata(navigator: ArrShellNavigator, serverId: ServerId) {
        metadataRoute(serverId)?.let(navigator::open)
    }
}

/**
 * Everything one server's connect screens need: the offline store and
 * action queue from the data graph plus the Retrofit interfaces built by
 * the composition root from [SonarrSettingsConnectConnection].
 */
class SonarrConnectClients(
    val store: EntityStore,
    val actionQueue: ActionQueue,
    val apis: SonarrSettingsConnectApis,
)

/**
 * Shell content for this group's nested routes. Settings screens do not
 * subscribe to live events: they refresh through the TTL and on
 * pull-to-refresh, so no refresh signals are plumbed here.
 */
fun sonarrSettingsConnectShellContent(
    clients: (ServerId) -> SonarrConnectClients,
    serverNames: (ServerId) -> String? = { null },
): ArrShellContent = ArrShellContent(
    feature = { route ->
        SonarrSettingsConnectFeatureSlot(
            route = route,
            clients = clients,
            serverNames = serverNames,
        )
    },
)

/**
 * Feature-slot entry: renders this group's nested routes and an empty state
 * for anything else, mirroring the quality feature's slot so a partial
 * integration never shows a blank screen.
 */
@Composable
fun SonarrSettingsConnectFeatureSlot(
    route: ArrRoute.Feature,
    clients: (ServerId) -> SonarrConnectClients,
    serverNames: (ServerId) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    if (route.owner != SonarrSettingsConnectFeature.ROUTE_OWNER) {
        ForeignOwnerPlaceholder(route.owner, modifier)
        return
    }
    val segments = route.path.split('/')
    val navigator = LocalArrShellNavigator.current

    val indexers = matchRoutePattern(SonarrSettingsConnectFeature.INDEXERS_PATTERN, segments)
    val indexerEditor =
        matchRoutePattern(SonarrSettingsConnectFeature.INDEXER_EDITOR_PATTERN, segments)
    val downloadClients =
        matchRoutePattern(SonarrSettingsConnectFeature.DOWNLOAD_CLIENTS_PATTERN, segments)
    val downloadClientEditor =
        matchRoutePattern(SonarrSettingsConnectFeature.DOWNLOAD_CLIENT_EDITOR_PATTERN, segments)
    val mappings =
        matchRoutePattern(SonarrSettingsConnectFeature.REMOTE_PATH_MAPPINGS_PATTERN, segments)
    val importLists = matchRoutePattern(SonarrSettingsConnectFeature.IMPORT_LISTS_PATTERN, segments)
    val importListEditor =
        matchRoutePattern(SonarrSettingsConnectFeature.IMPORT_LIST_EDITOR_PATTERN, segments)
    val exclusions =
        matchRoutePattern(SonarrSettingsConnectFeature.IMPORT_LIST_EXCLUSIONS_PATTERN, segments)
    val notifications =
        matchRoutePattern(SonarrSettingsConnectFeature.NOTIFICATIONS_PATTERN, segments)
    val notificationEditor =
        matchRoutePattern(SonarrSettingsConnectFeature.NOTIFICATION_EDITOR_PATTERN, segments)
    val metadata = matchRoutePattern(SonarrSettingsConnectFeature.METADATA_PATTERN, segments)

    when {
        indexers != null -> {
            val serverId = ServerId(indexers.getValue("serverId"))
            SonarrIndexersDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                onOpenIndexer = remember(navigator, serverId) { { indexerId ->
                    SonarrSettingsConnectFeature.openIndexerEditor(navigator, serverId, indexerId)
                } },
                modifier = modifier,
            )
        }

        indexerEditor != null -> {
            val serverId = ServerId(indexerEditor.getValue("serverId"))
            SonarrIndexerEditorDestination(
                serverId = serverId,
                indexerId = indexerEditor.getValue("indexerId").toIntOrNull(),
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                modifier = modifier,
            )
        }

        downloadClients != null -> {
            val serverId = ServerId(downloadClients.getValue("serverId"))
            SonarrDownloadClientsDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                onOpenClient = { clientId ->
                    SonarrSettingsConnectFeature.openDownloadClientEditor(
                        navigator,
                        serverId,
                        clientId,
                    )
                },
                onOpenMappings = remember(navigator, serverId) { {
                    SonarrSettingsConnectFeature.openRemotePathMappings(navigator, serverId)
                } },
                modifier = modifier,
            )
        }

        downloadClientEditor != null -> {
            val serverId = ServerId(downloadClientEditor.getValue("serverId"))
            SonarrDownloadClientEditorDestination(
                serverId = serverId,
                clientId = downloadClientEditor.getValue("clientId").toIntOrNull(),
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                modifier = modifier,
            )
        }

        mappings != null -> {
            val serverId = ServerId(mappings.getValue("serverId"))
            SonarrRemotePathMappingsDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                modifier = modifier,
            )
        }

        importLists != null -> {
            val serverId = ServerId(importLists.getValue("serverId"))
            SonarrImportListsDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                onOpenList = remember(navigator, serverId) { { listId ->
                    SonarrSettingsConnectFeature.openImportListEditor(navigator, serverId, listId)
                } },
                onOpenExclusions = remember(navigator, serverId) { {
                    SonarrSettingsConnectFeature.openImportListExclusions(navigator, serverId)
                } },
                modifier = modifier,
            )
        }

        importListEditor != null -> {
            val serverId = ServerId(importListEditor.getValue("serverId"))
            SonarrImportListEditorDestination(
                serverId = serverId,
                listId = importListEditor.getValue("listId").toIntOrNull(),
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                modifier = modifier,
            )
        }

        exclusions != null -> {
            val serverId = ServerId(exclusions.getValue("serverId"))
            SonarrImportListExclusionsDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                modifier = modifier,
            )
        }

        notifications != null -> {
            val serverId = ServerId(notifications.getValue("serverId"))
            SonarrNotificationsDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                onOpenNotification = { notificationId ->
                    SonarrSettingsConnectFeature.openNotificationEditor(
                        navigator,
                        serverId,
                        notificationId,
                    )
                },
                modifier = modifier,
            )
        }

        notificationEditor != null -> {
            val serverId = ServerId(notificationEditor.getValue("serverId"))
            SonarrNotificationEditorDestination(
                serverId = serverId,
                notificationId = notificationEditor.getValue("notificationId").toIntOrNull(),
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                modifier = modifier,
            )
        }

        metadata != null -> {
            val serverId = ServerId(metadata.getValue("serverId"))
            SonarrMetadataDestination(
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
