package dev.appolon.homelab.arr.feature.lidarr.settings.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.system.ui.LidarrAutoTaggingDestination
import dev.appolon.homelab.arr.feature.lidarr.settings.system.ui.LidarrAutoTaggingEditorDestination
import dev.appolon.homelab.arr.feature.lidarr.settings.system.ui.LidarrBackupDestination
import dev.appolon.homelab.arr.feature.lidarr.settings.system.ui.LidarrGeneralSettingsDestination
import dev.appolon.homelab.arr.feature.lidarr.settings.system.ui.LidarrSystemStatusDestination
import dev.appolon.homelab.arr.feature.lidarr.settings.system.ui.LidarrTagsDestination
import dev.appolon.homelab.arr.feature.lidarr.settings.system.ui.LidarrUpdatesDestination
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Everything the arr shell needs from the Lidarr general, system, backup,
 * updates, and tags settings group: nested route patterns, open helpers,
 * and the slot composable for [ArrShellContent]. The integration layer
 * calls [register] once before composing the shell.
 */
object LidarrSettingsSystemFeature {
    /** Shared with the other Lidarr screen groups; patterns are settings-specific. */
    const val ROUTE_OWNER = "lidarr"

    /** Host config: host, security, logging, analytics, and proxy display. */
    const val GENERAL_PATTERN = "settings/general/{serverId}"

    /** Backup list, schedule, restore, delete, and create action. */
    const val BACKUP_PATTERN = "settings/backup/{serverId}"

    /** Current version card, update check, install trigger, log files. */
    const val UPDATES_PATTERN = "settings/updates/{serverId}"

    /** Status, health, disk space, and scheduled tasks. */
    const val SYSTEM_STATUS_PATTERN = "settings/system/{serverId}"

    /** Tags with per-tag usage details. */
    const val TAGS_PATTERN = "settings/tags/{serverId}"

    /** Auto tagging rules list. */
    const val AUTO_TAGGING_PATTERN = "settings/autotagging/{serverId}"

    /** Auto tagging editor; ruleId "new" creates. */
    const val AUTO_TAGGING_EDITOR_PATTERN = "settings/autotagging/{serverId}/{ruleId}"

    val PATTERNS = listOf(
        GENERAL_PATTERN,
        BACKUP_PATTERN,
        UPDATES_PATTERN,
        SYSTEM_STATUS_PATTERN,
        TAGS_PATTERN,
        AUTO_TAGGING_PATTERN,
        AUTO_TAGGING_EDITOR_PATTERN,
    )

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun generalRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(GENERAL_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun backupRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(BACKUP_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun updatesRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(UPDATES_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun systemStatusRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(SYSTEM_STATUS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun tagsRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(TAGS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun autoTaggingRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(AUTO_TAGGING_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun autoTaggingEditorRoute(serverId: ServerId, ruleId: Int?): ArrRoute.Feature? =
        fillPattern(
            AUTO_TAGGING_EDITOR_PATTERN,
            mapOf("serverId" to serverId.value, "ruleId" to (ruleId?.toString() ?: "new")),
        )?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun openGeneral(navigator: ArrShellNavigator, serverId: ServerId) {
        generalRoute(serverId)?.let(navigator::open)
    }

    fun openBackup(navigator: ArrShellNavigator, serverId: ServerId) {
        backupRoute(serverId)?.let(navigator::open)
    }

    fun openUpdates(navigator: ArrShellNavigator, serverId: ServerId) {
        updatesRoute(serverId)?.let(navigator::open)
    }

    fun openSystemStatus(navigator: ArrShellNavigator, serverId: ServerId) {
        systemStatusRoute(serverId)?.let(navigator::open)
    }

    fun openTags(navigator: ArrShellNavigator, serverId: ServerId) {
        tagsRoute(serverId)?.let(navigator::open)
    }

    fun openAutoTagging(navigator: ArrShellNavigator, serverId: ServerId) {
        autoTaggingRoute(serverId)?.let(navigator::open)
    }

    fun openAutoTaggingEditor(navigator: ArrShellNavigator, serverId: ServerId, ruleId: Int?) {
        autoTaggingEditorRoute(serverId, ruleId)?.let(navigator::open)
    }
}

/**
 * Everything one server's screens in this group need: the offline store
 * and action queue from the data graph plus the Retrofit interfaces built
 * by the composition root from [LidarrSettingsSystemConnection].
 */
class LidarrSettingsSystemClients(
    val store: EntityStore,
    val actionQueue: ActionQueue,
    val apis: LidarrSettingsSystemApis,
)

/**
 * Shell content for this group's nested routes. Settings screens do not
 * subscribe to live events: they refresh through the TTL and on
 * pull-to-refresh, so no refresh signals are plumbed here. [onOpenLink]
 * and [onDownloadBackup] keep external navigation in the integration
 * layer; when absent the matching actions hide.
 */
fun lidarrSettingsSystemShellContent(
    clients: (ServerId) -> LidarrSettingsSystemClients,
    serverNames: (ServerId) -> String? = { null },
    onOpenLink: ((String) -> Unit)? = null,
    onDownloadBackup: ((
        serverId: ServerId,
        backup: dev.appolon.homelab.arr.api.lidarr.model.BackupResource,
    ) -> Unit)? = null,
): ArrShellContent = ArrShellContent(
    feature = { route ->
        LidarrSettingsSystemFeatureSlot(
            route = route,
            clients = clients,
            serverNames = serverNames,
            onOpenLink = onOpenLink,
            onDownloadBackup = onDownloadBackup,
        )
    },
)

/**
 * Feature-slot entry: renders this group's nested routes and an empty state
 * for anything else, mirroring the connect feature's slot so a partial
 * integration never shows a blank screen.
 */
@Composable
fun LidarrSettingsSystemFeatureSlot(
    route: ArrRoute.Feature,
    clients: (ServerId) -> LidarrSettingsSystemClients,
    serverNames: (ServerId) -> String? = { null },
    onOpenLink: ((String) -> Unit)? = null,
    onDownloadBackup: ((
        serverId: ServerId,
        backup: dev.appolon.homelab.arr.api.lidarr.model.BackupResource,
    ) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (route.owner != LidarrSettingsSystemFeature.ROUTE_OWNER) {
        ForeignOwnerPlaceholder(route.owner, modifier)
        return
    }
    val segments = route.path.split('/')
    val navigator = LocalArrShellNavigator.current

    val general = matchRoutePattern(LidarrSettingsSystemFeature.GENERAL_PATTERN, segments)
    val backup = matchRoutePattern(LidarrSettingsSystemFeature.BACKUP_PATTERN, segments)
    val updates = matchRoutePattern(LidarrSettingsSystemFeature.UPDATES_PATTERN, segments)
    val status = matchRoutePattern(LidarrSettingsSystemFeature.SYSTEM_STATUS_PATTERN, segments)
    val tags = matchRoutePattern(LidarrSettingsSystemFeature.TAGS_PATTERN, segments)
    val autoTagging = matchRoutePattern(LidarrSettingsSystemFeature.AUTO_TAGGING_PATTERN, segments)
    val autoTaggingEditor =
        matchRoutePattern(LidarrSettingsSystemFeature.AUTO_TAGGING_EDITOR_PATTERN, segments)

    when {
        general != null -> {
            val serverId = ServerId(general.getValue("serverId"))
            LidarrGeneralSettingsDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                onOpenLink = onOpenLink,
                modifier = modifier,
            )
        }

        backup != null -> {
            val serverId = ServerId(backup.getValue("serverId"))
            LidarrBackupDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                onDownloadBackup = onDownloadBackup?.let { download ->
                    { backupFile -> download(serverId, backupFile) }
                },
                modifier = modifier,
            )
        }

        updates != null -> {
            val serverId = ServerId(updates.getValue("serverId"))
            LidarrUpdatesDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                modifier = modifier,
            )
        }

        status != null -> {
            val serverId = ServerId(status.getValue("serverId"))
            LidarrSystemStatusDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                onOpenLink = onOpenLink,
                modifier = modifier,
            )
        }

        tags != null -> {
            val serverId = ServerId(tags.getValue("serverId"))
            LidarrTagsDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                modifier = modifier,
            )
        }

        autoTagging != null -> {
            val serverId = ServerId(autoTagging.getValue("serverId"))
            LidarrAutoTaggingDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = remember(navigator) { { navigator.back() } },
                onOpenEditor = remember(navigator, serverId) { { ruleId ->
                    LidarrSettingsSystemFeature.openAutoTaggingEditor(navigator, serverId, ruleId)
                } },
                modifier = modifier,
            )
        }

        autoTaggingEditor != null -> {
            val serverId = ServerId(autoTaggingEditor.getValue("serverId"))
            LidarrAutoTaggingEditorDestination(
                serverId = serverId,
                ruleId = autoTaggingEditor.getValue("ruleId").toIntOrNull(),
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
