package dev.appolon.homelab.arr.feature.bazarr.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.bazarr.settings.data.BazarrSettingsRepository
import dev.appolon.homelab.arr.feature.bazarr.settings.ui.BazarrGeneralScreen
import dev.appolon.homelab.arr.feature.bazarr.settings.ui.BazarrLanguageProfilesScreen
import dev.appolon.homelab.arr.feature.bazarr.settings.ui.BazarrLanguagesScreen
import dev.appolon.homelab.arr.feature.bazarr.settings.ui.BazarrNotificationsScreen
import dev.appolon.homelab.arr.feature.bazarr.settings.ui.BazarrProvidersScreen
import dev.appolon.homelab.arr.feature.bazarr.settings.ui.BazarrSchedulerScreen
import dev.appolon.homelab.arr.feature.bazarr.settings.ui.BazarrSettingsHomeScreen
import dev.appolon.homelab.arr.feature.bazarr.settings.ui.BazarrSystemScreen
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.GeneralViewModel
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.LanguagesViewModel
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.NotificationsViewModel
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.ProfilesViewModel
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.ProvidersViewModel
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.SchedulerViewModel
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.SystemViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Shell integration for the Bazarr settings screens: the nested routes this
 * group owns, open helpers for sibling groups and the shell, and the feature
 * slot composable. The integration layer calls [register] once before
 * composing the shell.
 */
object BazarrSettingsFeature {
    const val ROUTE_OWNER = "bazarr-settings"

    /** Settings home: the section list. */
    const val HOME_PATTERN = "settings/{serverId}"

    /** Enabled languages with hearing impaired and forced support. */
    const val LANGUAGES_PATTERN = "settings/{serverId}/languages"

    /** Language profiles with cutoff and must-contain rules. */
    const val PROFILES_PATTERN = "settings/{serverId}/profiles"

    /** Subtitle provider health and reset. */
    const val PROVIDERS_PATTERN = "settings/{serverId}/providers"

    /** Inbound webhook matrix and connection test. */
    const val NOTIFICATIONS_PATTERN = "settings/{serverId}/notifications"

    /** Task intervals, next runs, and run now. */
    const val SCHEDULER_PATTERN = "settings/{serverId}/scheduler"

    /** General settings projected from the status payload. */
    const val GENERAL_PATTERN = "settings/{serverId}/general"

    /** Status, jobs, backups, and logs. */
    const val SYSTEM_PATTERN = "settings/{serverId}/system"

    val PATTERNS = listOf(
        HOME_PATTERN,
        LANGUAGES_PATTERN,
        PROFILES_PATTERN,
        PROVIDERS_PATTERN,
        NOTIFICATIONS_PATTERN,
        SCHEDULER_PATTERN,
        GENERAL_PATTERN,
        SYSTEM_PATTERN,
    )

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openSettings(navigator: ArrShellNavigator, serverId: ServerId) {
        open(navigator, HOME_PATTERN, serverId)
    }

    fun openLanguages(navigator: ArrShellNavigator, serverId: ServerId) {
        open(navigator, LANGUAGES_PATTERN, serverId)
    }

    fun openProviders(navigator: ArrShellNavigator, serverId: ServerId) {
        open(navigator, PROVIDERS_PATTERN, serverId)
    }

    fun openSystem(navigator: ArrShellNavigator, serverId: ServerId) {
        open(navigator, SYSTEM_PATTERN, serverId)
    }

    private fun open(navigator: ArrShellNavigator, pattern: String, serverId: ServerId) {
        fillPattern(pattern, mapOf("serverId" to serverId.value))
            ?.let { navigator.open(ArrRoute.Feature(ROUTE_OWNER, it)) }
    }
}

/**
 * Feature-slot entry: renders this group's nested routes and defers to the
 * previous slot for foreign owners, so an integration layer can stack
 * several feature slots in one shell content.
 */
fun ArrShellContent.withBazarrSettings(graph: ArrDataGraph): ArrShellContent {
    val previousFeature = feature
    return ArrShellContent(
        serverList = serverList,
        serverHome = serverHome,
        library = library,
        detail = detail,
        add = add,
        queue = queue,
        calendar = calendar,
        settings = settings,
        feature = { route ->
            if (route.owner == BazarrSettingsFeature.ROUTE_OWNER) {
                BazarrSettingsFeatureSlot(route, graph)
            } else {
                previousFeature?.invoke(route)
            }
        },
    )
}

/** Dispatches one feature route of this owner to its destination composable. */
@Composable
fun BazarrSettingsFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val segments = route.path.split('/')
    val pattern = BazarrSettingsFeature.PATTERNS
        .firstOrNull { matchRoutePattern(it, segments) != null }
        ?: return
    val params = matchRoutePattern(pattern, segments) ?: return
    val serverId = ServerId(params.getValue("serverId"))
    when (pattern) {
        BazarrSettingsFeature.LANGUAGES_PATTERN -> LanguagesDestination(serverId, graph, modifier)
        BazarrSettingsFeature.PROFILES_PATTERN -> ProfilesDestination(serverId, graph, modifier)
        BazarrSettingsFeature.PROVIDERS_PATTERN -> ProvidersDestination(serverId, graph, modifier)
        BazarrSettingsFeature.NOTIFICATIONS_PATTERN ->
            NotificationsDestination(serverId, graph, modifier)

        BazarrSettingsFeature.SCHEDULER_PATTERN -> SchedulerDestination(serverId, graph, modifier)
        BazarrSettingsFeature.GENERAL_PATTERN -> GeneralDestination(serverId, graph, modifier)
        BazarrSettingsFeature.SYSTEM_PATTERN -> SystemDestination(serverId, graph, modifier)
        else -> SettingsHomeDestination(serverId, graph, modifier)
    }
}

/**
 * Connects to the server once and hands every settings destination the same
 * repository over the entity store and action queue. The connection gate
 * keeps the ViewModel constructors synchronous and testable.
 */
@Composable
private fun rememberSettingsRepository(
    serverId: ServerId,
    graph: ArrDataGraph,
): BazarrSettingsRepository? {
    var repository by remember(serverId) { mutableStateOf<BazarrSettingsRepository?>(null) }
    LaunchedEffect(serverId) {
        repository = BazarrSettingsConnection(graph.registry, graph.secrets)
            .connect(serverId)
            ?.let { apis ->
                BazarrSettingsRepository(
                    serverId = serverId,
                    store = graph.entityStore,
                    languagesApi = apis.languagesApi,
                    languageProfilesApi = apis.languageProfilesApi,
                    providersApi = apis.providersApi,
                    webhookSettingsApi = apis.webhookSettingsApi,
                    tasksApi = apis.tasksApi,
                    jobsApi = apis.jobsApi,
                    statusApi = apis.statusApi,
                    backupsApi = apis.backupsApi,
                    logsApi = apis.logsApi,
                    actionQueue = graph.actionQueue,
                )
            }
    }
    return repository
}

/** Spinner pane shown while the per-server connection resolves. */
@Composable
private fun SettingsConnecting(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Settings home destination. */
@Composable
fun SettingsHomeDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalArrShellNavigator.current
    val open: (String) -> Unit = { pattern ->
        fillPattern(pattern, mapOf("serverId" to serverId.value))
            ?.let { navigator.open(ArrRoute.Feature(BazarrSettingsFeature.ROUTE_OWNER, it)) }
    }
    BazarrSettingsHomeScreen(
        onBack = { navigator.back() },
        onOpenLanguages = { open(BazarrSettingsFeature.LANGUAGES_PATTERN) },
        onOpenProfiles = { open(BazarrSettingsFeature.PROFILES_PATTERN) },
        onOpenProviders = { open(BazarrSettingsFeature.PROVIDERS_PATTERN) },
        onOpenNotifications = { open(BazarrSettingsFeature.NOTIFICATIONS_PATTERN) },
        onOpenScheduler = { open(BazarrSettingsFeature.SCHEDULER_PATTERN) },
        onOpenGeneral = { open(BazarrSettingsFeature.GENERAL_PATTERN) },
        onOpenSystem = { open(BazarrSettingsFeature.SYSTEM_PATTERN) },
        modifier = modifier,
    )
}

/** Languages settings destination. */
@Composable
fun LanguagesDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    if (repository == null) {
        SettingsConnecting(modifier)
        return
    }
    val vm: LanguagesViewModel = viewModel(
        key = "bazarr-settings-languages-" + serverId.value,
        factory = viewModelFactory { initializer { LanguagesViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    BazarrLanguagesScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        modifier = modifier,
    )
}

/** Language profiles destination. */
@Composable
fun ProfilesDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    if (repository == null) {
        SettingsConnecting(modifier)
        return
    }
    val vm: ProfilesViewModel = viewModel(
        key = "bazarr-settings-profiles-" + serverId.value,
        factory = viewModelFactory { initializer { ProfilesViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    BazarrLanguageProfilesScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        modifier = modifier,
    )
}

/** Providers destination. */
@Composable
fun ProvidersDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    if (repository == null) {
        SettingsConnecting(modifier)
        return
    }
    val vm: ProvidersViewModel = viewModel(
        key = "bazarr-settings-providers-" + serverId.value,
        factory = viewModelFactory { initializer { ProvidersViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    BazarrProvidersScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        onResetProviders = vm::resetProviders,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}

/** Notifications destination. */
@Composable
fun NotificationsDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    if (repository == null) {
        SettingsConnecting(modifier)
        return
    }
    val vm: NotificationsViewModel = viewModel(
        key = "bazarr-settings-notifications-" + serverId.value,
        factory = viewModelFactory { initializer { NotificationsViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    BazarrNotificationsScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onTestWebhook = vm::testWebhook,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}

/** Scheduler destination. */
@Composable
fun SchedulerDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    if (repository == null) {
        SettingsConnecting(modifier)
        return
    }
    val vm: SchedulerViewModel = viewModel(
        key = "bazarr-settings-scheduler-" + serverId.value,
        factory = viewModelFactory { initializer { SchedulerViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    BazarrSchedulerScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        onRunNow = vm::runNow,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}

/** General settings destination. */
@Composable
fun GeneralDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    if (repository == null) {
        SettingsConnecting(modifier)
        return
    }
    val vm: GeneralViewModel = viewModel(
        key = "bazarr-settings-general-" + serverId.value,
        factory = viewModelFactory { initializer { GeneralViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    BazarrGeneralScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        modifier = modifier,
    )
}

/** System destination with the status, jobs, backups, and logs tabs. */
@Composable
fun SystemDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    val repository = rememberSettingsRepository(serverId, graph)
    if (repository == null) {
        SettingsConnecting(modifier)
        return
    }
    val vm: SystemViewModel = viewModel(
        key = "bazarr-settings-system-" + serverId.value,
        factory = viewModelFactory { initializer { SystemViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current

    BazarrSystemScreen(
        state = state,
        lce = lce,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        onTab = vm::setTab,
        onJobAction = vm::jobAction,
        onDeleteJob = vm::deleteJob,
        onEmptyQueue = vm::emptyQueue,
        onCreateBackup = vm::createBackup,
        onRestoreBackup = vm::restoreBackup,
        onDeleteBackup = vm::deleteBackup,
        onRotateLogs = vm::rotateLogs,
        onLogTypeFilter = vm::setLogTypeFilter,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}
