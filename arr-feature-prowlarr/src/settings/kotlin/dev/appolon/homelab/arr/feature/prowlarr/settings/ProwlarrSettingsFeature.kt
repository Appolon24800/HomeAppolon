package dev.appolon.homelab.arr.feature.prowlarr.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ProwlarrIndexersFeature
import dev.appolon.homelab.arr.feature.prowlarr.settings.data.ProwlarrSettingsRepository
import dev.appolon.homelab.arr.feature.prowlarr.settings.ui.ProwlarrDownloadClientEditorScreen
import dev.appolon.homelab.arr.feature.prowlarr.settings.ui.ProwlarrDownloadClientsScreen
import dev.appolon.homelab.arr.feature.prowlarr.settings.ui.ProwlarrGeneralSettingsScreen
import dev.appolon.homelab.arr.feature.prowlarr.settings.ui.ProwlarrNotificationEditorScreen
import dev.appolon.homelab.arr.feature.prowlarr.settings.ui.ProwlarrNotificationsScreen
import dev.appolon.homelab.arr.feature.prowlarr.settings.ui.ProwlarrSystemStatusScreen
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrDownloadClientEditorViewModel
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrDownloadClientsViewModel
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrGeneralSettingsViewModel
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrNotificationEditorViewModel
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrNotificationsViewModel
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrSystemStatusViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Shell integration for the Prowlarr settings group: the nested routes this
 * source set owns (download clients with grab targets, connect
 * notifications with the event matrix, general settings, system status and
 * tasks), open helpers, and the feature slot. The integration layer calls
 * [register] once before composing the shell and merges
 * [prowlarrSettingsShellContent] into it.
 */
object ProwlarrSettingsFeature {
    const val ROUTE_OWNER = "prowlarr-settings"

    /** Download clients list with enable toggles and test-all. */
    const val DOWNLOAD_CLIENTS_PATTERN = "settings/downloadclients/{serverId}"

    /** Schema-driven download client builder; clientId "new" creates. */
    const val DOWNLOAD_CLIENT_EDITOR_PATTERN = "settings/downloadclients/{serverId}/{clientId}"

    /** Connect notifications list. */
    const val NOTIFICATIONS_PATTERN = "settings/notifications/{serverId}"

    /** Notification builder with the event matrix; notificationId "new" creates. */
    const val NOTIFICATION_EDITOR_PATTERN = "settings/notifications/{serverId}/{notificationId}"

    /** General settings: host, security, development, UI culture. */
    const val GENERAL_PATTERN = "settings/general/{serverId}"

    /** System status with health and the scheduled task table. */
    const val SYSTEM_PATTERN = "settings/system/{serverId}"

    val PATTERNS = listOf(
        DOWNLOAD_CLIENTS_PATTERN,
        DOWNLOAD_CLIENT_EDITOR_PATTERN,
        NOTIFICATIONS_PATTERN,
        NOTIFICATION_EDITOR_PATTERN,
        GENERAL_PATTERN,
        SYSTEM_PATTERN,
    )

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun downloadClientsRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(DOWNLOAD_CLIENTS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun downloadClientEditorRoute(serverId: ServerId, clientId: Int?): ArrRoute.Feature? =
        fillPattern(
            DOWNLOAD_CLIENT_EDITOR_PATTERN,
            mapOf("serverId" to serverId.value, "clientId" to (clientId?.toString() ?: "new")),
        )?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

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

    fun generalRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(GENERAL_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun systemRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(SYSTEM_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    /** Deep link into the indexers group's proxy manager. */
    fun indexerProxiesRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(ProwlarrIndexersFeature.PROXIES_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ProwlarrIndexersFeature.ROUTE_OWNER, it) }

    fun openDownloadClients(navigator: ArrShellNavigator, serverId: ServerId) {
        downloadClientsRoute(serverId)?.let(navigator::open)
    }

    fun openDownloadClientEditor(
        navigator: ArrShellNavigator,
        serverId: ServerId,
        clientId: Int?,
    ) {
        downloadClientEditorRoute(serverId, clientId)?.let(navigator::open)
    }

    fun openNotifications(navigator: ArrShellNavigator, serverId: ServerId) {
        notificationsRoute(serverId)?.let(navigator::open)
    }

    fun openNotificationEditor(
        navigator: ArrShellNavigator,
        serverId: ServerId,
        notificationId: Int?,
    ) {
        notificationEditorRoute(serverId, notificationId)?.let(navigator::open)
    }

    fun openGeneral(navigator: ArrShellNavigator, serverId: ServerId) {
        generalRoute(serverId)?.let(navigator::open)
    }

    fun openSystem(navigator: ArrShellNavigator, serverId: ServerId) {
        systemRoute(serverId)?.let(navigator::open)
    }

    fun openIndexerProxies(navigator: ArrShellNavigator, serverId: ServerId) {
        indexerProxiesRoute(serverId)?.let(navigator::open)
    }
}

/**
 * Everything one server's settings screens need: the offline store and
 * action queue from the data graph plus the Retrofit interfaces built by
 * the composition root from [ProwlarrSettingsConnection].
 */
class ProwlarrSettingsClients(
    val store: EntityStore,
    val actionQueue: ActionQueue,
    val apis: ProwlarrSettingsFeatureApis,
)

/**
 * Shell content for this group's nested routes. Settings screens refresh
 * through the TTL and on pull-to-refresh, so no live-event plumbing runs
 * here.
 */
fun prowlarrSettingsShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    feature = { route -> ProwlarrSettingsFeatureSlot(route, graph) },
)

/**
 * Feature-slot entry: renders this group's nested routes and renders
 * nothing for anything else, so partial integrations never show a blank
 * screen where another group owns the route.
 */
@Composable
fun ProwlarrSettingsFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != ProwlarrSettingsFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val clients = matchRoutePattern(ProwlarrSettingsFeature.DOWNLOAD_CLIENTS_PATTERN, segments)
    val clientEditor =
        matchRoutePattern(ProwlarrSettingsFeature.DOWNLOAD_CLIENT_EDITOR_PATTERN, segments)
    val notifications =
        matchRoutePattern(ProwlarrSettingsFeature.NOTIFICATIONS_PATTERN, segments)
    val notificationEditor =
        matchRoutePattern(ProwlarrSettingsFeature.NOTIFICATION_EDITOR_PATTERN, segments)
    val general = matchRoutePattern(ProwlarrSettingsFeature.GENERAL_PATTERN, segments)
    val system = matchRoutePattern(ProwlarrSettingsFeature.SYSTEM_PATTERN, segments)

    when {
        clientEditor != null -> ProwlarrDownloadClientEditorDestination(
            serverId = ServerId(clientEditor.getValue("serverId")),
            clientId = clientEditor.getValue("clientId").toIntOrNull(),
            graph = graph,
            modifier = modifier,
        )

        clients != null -> ProwlarrDownloadClientsDestination(
            serverId = ServerId(clients.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )

        notificationEditor != null -> ProwlarrNotificationEditorDestination(
            serverId = ServerId(notificationEditor.getValue("serverId")),
            notificationId = notificationEditor.getValue("notificationId").toIntOrNull(),
            graph = graph,
            modifier = modifier,
        )

        notifications != null -> ProwlarrNotificationsDestination(
            serverId = ServerId(notifications.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )

        general != null -> ProwlarrGeneralSettingsDestination(
            serverId = ServerId(general.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )

        system != null -> ProwlarrSystemStatusDestination(
            serverId = ServerId(system.getValue("serverId")),
            graph = graph,
            modifier = modifier,
        )
    }
}

/**
 * One connected repository per screen. The connection resolves
 * asynchronously; a spinner covers the gap, then the ViewModel builds on
 * the repository.
 */
@Composable
private fun rememberSettingsRepository(
    serverId: ServerId,
    graph: ArrDataGraph,
): ProwlarrSettingsRepository? {
    val connection = remember(serverId) {
        ProwlarrSettingsConnection(graph.registry, graph.secrets)
    }
    var repository by remember(serverId) {
        mutableStateOf<ProwlarrSettingsRepository?>(null)
    }
    LaunchedEffect(serverId) {
        repository = connection.connect(serverId)?.let { apis ->
            ProwlarrSettingsRepository(
                serverId = serverId,
                store = graph.entityStore,
                apis = apis,
                actionQueue = graph.actionQueue,
            )
        }
    }
    return repository
}

@Composable
private fun RepositoryGate(
    repository: ProwlarrSettingsRepository?,
    modifier: Modifier = Modifier,
    content: @Composable (ProwlarrSettingsRepository) -> Unit,
) {
    val repo = repository
    if (repo == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    content(repo)
}

/** Destination for the download clients list route. */
@Composable
fun ProwlarrDownloadClientsDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    RepositoryGate(repository, modifier) { repo ->
        val vm: ProwlarrDownloadClientsViewModel = viewModel(
            key = "prowlarr-settings-clients-" + serverId.value,
        ) {
            ProwlarrDownloadClientsViewModel(serverId, repo)
        }
        val state by vm.state.collectAsState()
        val lce by vm.lce.collectAsState()
        val navigator = LocalArrShellNavigator.current

        ProwlarrDownloadClientsScreen(
            state = state,
            lce = lce,
            serverName = null,
            onBack = { navigator.back() },
            onRefresh = vm::refresh,
            onOpen = { clientId ->
                ProwlarrSettingsFeature.openDownloadClientEditor(navigator, serverId, clientId)
            },
            onAdd = {
                ProwlarrSettingsFeature.openDownloadClientEditor(navigator, serverId, null)
            },
            onTestAll = vm::testAll,
            onToggleEnabled = vm::setEnabled,
            onDeleteRequest = vm::requestDelete,
            onDeleteCancelled = vm::deleteCancelled,
            onDeleteConfirmed = vm::deleteConfirmed,
            onConsumeNotice = vm::consumeNotice,
            onConsumeWriteError = vm::consumeWriteError,
            modifier = Modifier,
        )
    }
}

/** Destination for the download client editor route (clientId null creates). */
@Composable
fun ProwlarrDownloadClientEditorDestination(
    serverId: ServerId,
    clientId: Int?,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    RepositoryGate(repository, modifier) { repo ->
        val vm: ProwlarrDownloadClientEditorViewModel = viewModel(
            key = "prowlarr-settings-client-editor-" + serverId.value + "-" + (clientId ?: "new"),
        ) {
            ProwlarrDownloadClientEditorViewModel(serverId, clientId, repo)
        }
        val state by vm.state.collectAsState()
        val lce by vm.lce.collectAsState()
        val navigator = LocalArrShellNavigator.current

        ProwlarrDownloadClientEditorScreen(
            state = state,
            lce = lce,
            serverName = null,
            onBack = { navigator.back() },
            onPick = vm::pickSchema,
            onNameChange = { name -> vm.updateDraft { it.copy(name = name) } },
            onEnableChange = { enable -> vm.updateDraft { it.copy(enable = enable) } },
            onPriorityChange = vm::setPriority,
            onValueChange = vm::onValueChange,
            onToggleTag = vm::toggleTag,
            onAddGrabTarget = vm::addGrabTarget,
            onRemoveGrabTarget = vm::removeGrabTarget,
            onGrabTargetCategoryChange = vm::setGrabTargetCategory,
            onToggleGrabTargetNewznab = vm::toggleGrabTargetNewznabCategory,
            onTest = vm::test,
            onSave = { vm.save { navigator.back() } },
            onDeleteRequest = vm::requestDelete,
            onDeleteCancelled = vm::deleteCancelled,
            onDeleteConfirmed = { vm.deleteConfirmed { navigator.back() } },
            onConsumeNotice = vm::consumeNotice,
            onConsumeWriteError = vm::consumeWriteError,
            modifier = Modifier,
        )
    }
}

/** Destination for the notifications list route. */
@Composable
fun ProwlarrNotificationsDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    RepositoryGate(repository, modifier) { repo ->
        val vm: ProwlarrNotificationsViewModel = viewModel(
            key = "prowlarr-settings-notifications-" + serverId.value,
        ) {
            ProwlarrNotificationsViewModel(serverId, repo)
        }
        val state by vm.state.collectAsState()
        val lce by vm.lce.collectAsState()
        val navigator = LocalArrShellNavigator.current

        ProwlarrNotificationsScreen(
            state = state,
            lce = lce,
            serverName = null,
            onBack = { navigator.back() },
            onRefresh = vm::refresh,
            onOpen = { notificationId ->
                ProwlarrSettingsFeature.openNotificationEditor(
                    navigator,
                    serverId,
                    notificationId,
                )
            },
            onAdd = {
                ProwlarrSettingsFeature.openNotificationEditor(navigator, serverId, null)
            },
            onTestAll = vm::testAll,
            onDeleteRequest = vm::requestDelete,
            onDeleteCancelled = vm::deleteCancelled,
            onDeleteConfirmed = vm::deleteConfirmed,
            onConsumeNotice = vm::consumeNotice,
            onConsumeWriteError = vm::consumeWriteError,
            modifier = Modifier,
        )
    }
}

/** Destination for the notification editor route (notificationId null creates). */
@Composable
fun ProwlarrNotificationEditorDestination(
    serverId: ServerId,
    notificationId: Int?,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    RepositoryGate(repository, modifier) { repo ->
        val vm: ProwlarrNotificationEditorViewModel = viewModel(
            key = "prowlarr-settings-notification-editor-" + serverId.value +
                "-" + (notificationId ?: "new"),
        ) {
            ProwlarrNotificationEditorViewModel(serverId, notificationId, repo)
        }
        val state by vm.state.collectAsState()
        val lce by vm.lce.collectAsState()
        val navigator = LocalArrShellNavigator.current

        ProwlarrNotificationEditorScreen(
            state = state,
            lce = lce,
            serverName = null,
            onBack = { navigator.back() },
            onPick = vm::pickSchema,
            onNameChange = { name -> vm.updateDraft { it.copy(name = name) } },
            onToggleEvent = vm::toggleEvent,
            onValueChange = vm::onValueChange,
            onToggleTag = vm::toggleTag,
            onTest = vm::test,
            onSave = { vm.save { navigator.back() } },
            onDeleteRequest = vm::requestDelete,
            onDeleteCancelled = vm::deleteCancelled,
            onDeleteConfirmed = { vm.deleteConfirmed { navigator.back() } },
            onConsumeNotice = vm::consumeNotice,
            onConsumeWriteError = vm::consumeWriteError,
            modifier = Modifier,
        )
    }
}

/** Destination for the general settings route. */
@Composable
fun ProwlarrGeneralSettingsDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    RepositoryGate(repository, modifier) { repo ->
        val vm: ProwlarrGeneralSettingsViewModel = viewModel(
            key = "prowlarr-settings-general-" + serverId.value,
        ) {
            ProwlarrGeneralSettingsViewModel(repo)
        }
        val state by vm.state.collectAsState()
        val lce by vm.lce.collectAsState()
        val navigator = LocalArrShellNavigator.current

        ProwlarrGeneralSettingsScreen(
            state = state,
            lce = lce,
            serverName = null,
            onBack = { navigator.back() },
            onRefresh = vm::refresh,
            onEditHost = vm::editHost,
            onEditDevelopment = vm::editDevelopment,
            onCultureSelect = vm::setUiLanguage,
            onSaveHost = vm::saveHost,
            onSaveDevelopment = vm::saveDevelopment,
            onSaveCulture = vm::saveCulture,
            onOpenIndexerProxies = {
                ProwlarrSettingsFeature.openIndexerProxies(navigator, serverId)
            },
            onConsumeNotice = vm::consumeNotice,
            onConsumeWriteError = vm::consumeWriteError,
            modifier = Modifier,
        )
    }
}

/** Destination for the system status route. */
@Composable
fun ProwlarrSystemStatusDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    RepositoryGate(repository, modifier) { repo ->
        val vm: ProwlarrSystemStatusViewModel = viewModel(
            key = "prowlarr-settings-system-" + serverId.value,
        ) {
            ProwlarrSystemStatusViewModel(repo)
        }
        val state by vm.state.collectAsState()
        val lce by vm.lce.collectAsState()
        val navigator = LocalArrShellNavigator.current

        ProwlarrSystemStatusScreen(
            state = state,
            lce = lce,
            serverName = null,
            onBack = { navigator.back() },
            onRefresh = vm::refresh,
            onConsumeWriteError = vm::consumeWriteError,
            modifier = Modifier,
        )
    }
}
