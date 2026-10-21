package dev.appolon.homelab.arr

import retrofit2.Retrofit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import dev.appolon.homelab.arr.net.ArrCommandRunner
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.appolon.homelab.arr.api.lidarr.LidarrQueueApis
import dev.appolon.homelab.arr.api.radarr.RadarrQueueApis
import dev.appolon.homelab.arr.api.radarr.RadarrSystemApis
import dev.appolon.homelab.arr.api.sonarr.SonarrQueueApis
import dev.appolon.homelab.R as AppR
import dev.appolon.homelab.arr.feature.lidarr.calendar.LidarrCalendarRoutes
import dev.appolon.homelab.arr.feature.lidarr.wanted.LidarrWantedRoutes
import dev.appolon.homelab.arr.feature.sonarr.calendar.SonarrCalendarRoutes
import dev.appolon.homelab.arr.feature.sonarr.wanted.SonarrWantedRoutes
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.feature.bazarr.BazarrFeatureRoot
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.settings.BazarrSettingsFeature
import dev.appolon.homelab.arr.feature.lidarr.LidarrFeatureRoot
import dev.appolon.homelab.arr.feature.lidarr.activity.LidarrActivityClients
import dev.appolon.homelab.arr.feature.lidarr.activity.LidarrActivityFeature
import dev.appolon.homelab.arr.feature.lidarr.activity.lidarrActivityShellContent
import dev.appolon.homelab.arr.feature.lidarr.addnew.LidarrAddNewFeature
import dev.appolon.homelab.arr.feature.lidarr.addnew.lidarrAddNewShellContent
import dev.appolon.homelab.arr.feature.lidarr.calendar.LidarrCalendarDeps
import dev.appolon.homelab.arr.feature.lidarr.calendar.withLidarrCalendar
import dev.appolon.homelab.arr.feature.lidarr.detail.LidarrArtistDetailDeps
import dev.appolon.homelab.arr.feature.lidarr.detail.withLidarrArtistDetail
import dev.appolon.homelab.arr.feature.lidarr.library.LidarrLibraryFeature
import dev.appolon.homelab.arr.feature.lidarr.library.lidarrLibraryShellContent
import dev.appolon.homelab.arr.feature.lidarr.queue.LidarrQueueDeps
import dev.appolon.homelab.arr.feature.lidarr.queue.withLidarrQueue
import dev.appolon.homelab.arr.feature.lidarr.search.LidarrSearchDeps
import dev.appolon.homelab.arr.feature.lidarr.search.withLidarrSearch
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.LidarrConnectClients
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.LidarrSettingsConnectConnection
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.LidarrSettingsConnectFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.lidarrSettingsConnectShellContent
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.LidarrMediaManagementFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.LidarrSettingsMmClients
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.LidarrSettingsMmConnection
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.lidarrSettingsMmShellContent
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.LidarrQualityClients
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.LidarrSettingsQualityConnection
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.LidarrSettingsQualityFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.lidarrSettingsQualityShellContent
import dev.appolon.homelab.arr.feature.lidarr.settings.system.LidarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.lidarr.settings.system.LidarrSettingsSystemConnection
import dev.appolon.homelab.arr.feature.lidarr.settings.system.LidarrSettingsSystemFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.system.lidarrSettingsSystemShellContent
import dev.appolon.homelab.arr.feature.lidarr.wanted.LidarrWantedDeps
import dev.appolon.homelab.arr.feature.lidarr.wanted.withLidarrWanted
import dev.appolon.homelab.arr.feature.prowlarr.ProwlarrFeatureRoot
import dev.appolon.homelab.arr.feature.prowlarr.settings.ProwlarrSettingsFeature
import dev.appolon.homelab.arr.feature.radarr.RadarrFeatureRoot
import dev.appolon.homelab.arr.feature.radarr.activity.RadarrActivityClients
import dev.appolon.homelab.arr.feature.radarr.activity.RadarrActivityFeature
import dev.appolon.homelab.arr.feature.radarr.activity.radarrActivityShellContent
import dev.appolon.homelab.arr.feature.radarr.addnew.RadarrAddNewFeature
import dev.appolon.homelab.arr.feature.radarr.addnew.radarrAddNewShellContent
import dev.appolon.homelab.arr.feature.radarr.calendar.RadarrCalendarFeature
import dev.appolon.homelab.arr.feature.radarr.calendar.radarrCalendarShellContent
import dev.appolon.homelab.arr.feature.radarr.detail.RadarrDetailFeature
import dev.appolon.homelab.arr.feature.radarr.detail.radarrDetailShellContent
import dev.appolon.homelab.arr.feature.radarr.library.RadarrLibraryFeature
import dev.appolon.homelab.arr.feature.radarr.library.radarrLibraryShellContent
import dev.appolon.homelab.arr.feature.radarr.queue.RadarrQueueClients
import dev.appolon.homelab.arr.feature.radarr.queue.RadarrQueueFeature
import dev.appolon.homelab.arr.feature.radarr.queue.data.QueueCommandBody
import dev.appolon.homelab.arr.feature.radarr.queue.data.QueueCommandRunner
import dev.appolon.homelab.arr.feature.radarr.queue.radarrQueueShellContent
import dev.appolon.homelab.arr.feature.radarr.search.RadarrSearchFeature
import dev.appolon.homelab.arr.feature.radarr.search.radarrSearchShellContent
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrConnectClients
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrSettingsConnectConnection
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrSettingsConnectFeature
import dev.appolon.homelab.arr.feature.radarr.settings.connect.radarrSettingsConnectShellContent
import dev.appolon.homelab.arr.feature.radarr.settings.mm.RadarrMediaManagementFeature
import dev.appolon.homelab.arr.feature.radarr.settings.mm.RadarrSettingsMmClients
import dev.appolon.homelab.arr.feature.radarr.settings.mm.RadarrSettingsMmConnection
import dev.appolon.homelab.arr.feature.radarr.settings.mm.radarrSettingsMmShellContent
import dev.appolon.homelab.arr.feature.radarr.settings.quality.RadarrQualityClients
import dev.appolon.homelab.arr.feature.radarr.settings.quality.RadarrSettingsQualityConnection
import dev.appolon.homelab.arr.feature.radarr.settings.quality.RadarrSettingsQualityFeature
import dev.appolon.homelab.arr.feature.radarr.settings.quality.radarrSettingsQualityShellContent
import dev.appolon.homelab.arr.feature.radarr.settings.system.RadarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.radarr.settings.system.RadarrSettingsSystemConnection
import dev.appolon.homelab.arr.feature.radarr.settings.system.RadarrSettingsSystemFeature
import dev.appolon.homelab.arr.feature.radarr.settings.system.radarrSettingsSystemShellContent
import dev.appolon.homelab.arr.feature.radarr.wanted.RadarrWantedFeature
import dev.appolon.homelab.arr.feature.radarr.wanted.radarrWantedShellContent
import dev.appolon.homelab.arr.feature.sonarr.SonarrFeatureRoot
import dev.appolon.homelab.arr.feature.sonarr.activity.SonarrActivityClients
import dev.appolon.homelab.arr.feature.sonarr.activity.SonarrActivityFeature
import dev.appolon.homelab.arr.feature.sonarr.activity.sonarrActivityShellContent
import dev.appolon.homelab.arr.feature.sonarr.addnew.SonarrAddDeps
import dev.appolon.homelab.arr.feature.sonarr.addnew.withSonarrAddNew
import dev.appolon.homelab.arr.feature.sonarr.calendar.SonarrCalendarDeps
import dev.appolon.homelab.arr.feature.sonarr.calendar.withSonarrCalendar
import dev.appolon.homelab.arr.feature.sonarr.detail.SonarrSeriesDetailDeps
import dev.appolon.homelab.arr.feature.sonarr.detail.withSonarrSeriesDetail
import dev.appolon.homelab.arr.feature.sonarr.library.SonarrLibraryDeps
import dev.appolon.homelab.arr.feature.sonarr.library.withSonarrSeriesLibrary
import dev.appolon.homelab.arr.feature.sonarr.queue.SonarrQueueDeps
import dev.appolon.homelab.arr.feature.sonarr.queue.withSonarrQueue
import dev.appolon.homelab.arr.feature.sonarr.search.SonarrSearchDeps
import dev.appolon.homelab.arr.feature.sonarr.search.withSonarrSearch
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.SonarrConnectClients
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.SonarrSettingsConnectConnection
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.SonarrSettingsConnectFeature
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.sonarrSettingsConnectShellContent
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.SonarrMediaManagementFeature
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.SonarrSettingsMmClients
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.SonarrSettingsMmConnection
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.sonarrSettingsMmShellContent
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.SonarrQualityClients
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.SonarrSettingsQualityConnection
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.SonarrSettingsQualityFeature
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.sonarrSettingsQualityShellContent
import dev.appolon.homelab.arr.feature.sonarr.settings.system.SonarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.sonarr.settings.system.SonarrSettingsSystemConnection
import dev.appolon.homelab.arr.feature.sonarr.settings.system.SonarrSettingsSystemFeature
import dev.appolon.homelab.arr.feature.sonarr.settings.system.sonarrSettingsSystemShellContent
import dev.appolon.homelab.arr.feature.sonarr.wanted.SonarrWantedDeps
import dev.appolon.homelab.arr.feature.sonarr.wanted.withSonarrWanted
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.servers.ServersFeature
import dev.appolon.homelab.arr.servers.serversShellContent
import dev.appolon.homelab.arr.sync.ArrEvent
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.ShellServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Builds the one [ArrShellContent] the dashboard's arr host renders, by
 * merging every feature bundle that is wireable from :app's dependency set.
 *
 * What :app can wire is bounded by the integration rules: project
 * dependencies only. Graph-backed groups (library, detail, add, calendar,
 * wanted, search, queue for Sonarr and Lidarr) connect internally and need
 * nothing from here. Groups whose clients are Retrofit-built here (settings,
 * activity, Radarr queue) expose suspend connections plus synchronous client
 * providers, so their bundles are pre-connected per server and gated at
 * dispatch time: the placeholder shows until the bundle lands.
 *
 * [liveEvents] is the live manager's event fan-out; it is scoped per feature
 * into the [androidx.coroutines.flow.Flow] refresh signals the queue and
 * activity screens collect.
 */
internal class ArrFeatureWiring(
    private val graph: ArrDataGraph,
    registry: ArrRouteRegistry,
    private val clientFactory: ArrClientFactory,
    scope: CoroutineScope,
    liveEvents: Flow<ArrEvent>,
) {
    /** Switcher display data for [dev.appolon.homelab.arr.ArrHost]. */
    val shellServers = MutableStateFlow<List<ShellServer>>(emptyList())

    /**
     * Per-server refresh signal for every queue and activity screen:
     * [ArrEvent.QueueChanged] mapped to the server whose queue moved.
     */
    private val queueRefreshSignals: Flow<ServerId> = liveEvents
        .filterIsInstance<ArrEvent.QueueChanged>()
        .map { it.serverId }

    // ---- group contents -------------------------------------------------

    private val serversContent = serversShellContent(graph)

    private val radarrLibrary = radarrLibraryShellContent(graph)
    private val radarrDetail = radarrDetailShellContent(graph)
    private val radarrAddNew = radarrAddNewShellContent(graph)
    private val radarrWanted = radarrWantedShellContent(graph)
    private val radarrCalendar = radarrCalendarShellContent(graph)
    private val radarrSearch = radarrSearchShellContent(graph)

    private val radarrMmBundles = settingsBundles { id ->
        RadarrSettingsMmConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            RadarrSettingsMmClients(graph.entityStore, graph.actionQueue, apis)
        }
    }
    private val radarrQualityBundles = settingsBundles { id ->
        RadarrSettingsQualityConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            RadarrQualityClients(graph.entityStore, graph.actionQueue, apis)
        }
    }
    private val radarrConnectBundles = settingsBundles { id ->
        RadarrSettingsConnectConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            RadarrConnectClients(graph.entityStore, graph.actionQueue, apis)
        }
    }
    private val radarrSystemBundles = settingsBundles { id ->
        RadarrSettingsSystemConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            RadarrSettingsSystemClients(graph.entityStore, graph.actionQueue, apis)
        }
    }

    private val radarrQueueBundles = settingsBundles { id ->
        serverHttp(id)?.let { http ->
            RadarrQueueClients(
                store = graph.entityStore,
                actionQueue = graph.actionQueue,
                queueApi = RadarrQueueApis.queue(http.retrofit),
                queueActionApi = RadarrQueueApis.queueAction(http.retrofit),
                queueDetailsApi = RadarrQueueApis.queueDetails(http.retrofit),
                queueStatusApi = RadarrQueueApis.queueStatus(http.retrofit),
                manualImportApi = RadarrQueueApis.manualImport(http.retrofit),
                fileSystemApi = RadarrSystemApis.fileSystem(http.retrofit),
                commandRunner = OkHttpRadarrQueueCommandRunner(http.client, http.baseUrl),
            )
        }
    }

    private val radarrActivityBundles = settingsBundles { id ->
        serverHttp(id)?.let { http ->
            RadarrActivityClients(
                store = graph.entityStore,
                actionQueue = graph.actionQueue,
                historyApi = RadarrQueueApis.history(http.retrofit),
                blocklistApi = RadarrQueueApis.blocklist(http.retrofit),
            )
        }
    }

    /** Feature routes (manual import) plus the built-in queue slot for Radarr. */
    private val radarrQueueContent = radarrQueueShellContent(
        clients = provider(radarrQueueBundles),
        refreshSignals = queueRefreshSignals,
    )

    private val sonarrMmBundles = settingsBundles { id ->
        SonarrSettingsMmConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            SonarrSettingsMmClients(graph.entityStore, graph.actionQueue, apis)
        }
    }
    private val sonarrQualityBundles = settingsBundles { id ->
        SonarrSettingsQualityConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            SonarrQualityClients(graph.entityStore, graph.actionQueue, apis)
        }
    }
    private val sonarrConnectBundles = settingsBundles { id ->
        SonarrSettingsConnectConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            SonarrConnectClients(graph.entityStore, graph.actionQueue, apis)
        }
    }

    private val sonarrSystemBundles = settingsBundles { id ->
        SonarrSettingsSystemConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            SonarrSettingsSystemClients(graph.entityStore, graph.actionQueue, apis)
        }
    }

    private val sonarrActivityBundles = settingsBundles { id ->
        serverHttp(id)?.let { http ->
            SonarrActivityClients(
                graph.entityStore,
                graph.actionQueue,
                SonarrQueueApis.history(http.retrofit),
                SonarrQueueApis.blocklist(http.retrofit),
            )
        }
    }

    private val lidarrMmBundles = settingsBundles { id ->
        LidarrSettingsMmConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            LidarrSettingsMmClients(graph.entityStore, graph.actionQueue, apis)
        }
    }

    private val lidarrQualityBundles = settingsBundles { id ->
        LidarrSettingsQualityConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            LidarrQualityClients(graph.entityStore, graph.actionQueue, apis)
        }
    }

    private val lidarrConnectBundles = settingsBundles { id ->
        LidarrSettingsConnectConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            LidarrConnectClients(graph.entityStore, graph.actionQueue, apis)
        }
    }

    private val lidarrSystemBundles = settingsBundles { id ->
        LidarrSettingsSystemConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            LidarrSettingsSystemClients(graph.entityStore, graph.actionQueue, apis)
        }
    }

    private val lidarrActivityBundles = settingsBundles { id ->
        serverHttp(id)?.let { http ->
            LidarrActivityClients(
                graph.entityStore,
                graph.actionQueue,
                LidarrQueueApis.history(http.retrofit),
                LidarrQueueApis.blocklist(http.retrofit),
            )
        }
    }

    private val radarrPlainGroups: List<Pair<List<String>, ArrShellContent>> = listOf(
        RadarrLibraryFeature.PATTERNS to radarrLibrary,
        RadarrDetailFeature.PATTERNS to radarrDetail,
        RadarrAddNewFeature.PATTERNS to radarrAddNew,
        RadarrWantedFeature.PATTERNS to radarrWanted,
        RadarrCalendarFeature.PATTERNS to radarrCalendar,
        RadarrSearchFeature.PATTERNS to radarrSearch,
    )

    private val radarrSettingsGroups: List<SettingsGroup<*>> = listOf(
        SettingsGroup(RadarrMediaManagementFeature.PATTERNS, radarrMmBundles, mmContent()),
        SettingsGroup(RadarrSettingsQualityFeature.PATTERNS, radarrQualityBundles, qualityContent()),
        SettingsGroup(RadarrSettingsConnectFeature.PATTERNS, radarrConnectBundles, connectContent()),
        SettingsGroup(RadarrSettingsSystemFeature.PATTERNS, radarrSystemBundles, systemContent()),
        SettingsGroup(
            RadarrQueueFeature.PATTERNS,
            radarrQueueBundles,
            radarrQueueContent,
        ),
        SettingsGroup(
            RadarrActivityFeature.PATTERNS,
            radarrActivityBundles,
            radarrActivityShellContent(provider(radarrActivityBundles), refreshSignals = queueRefreshSignals),
        ),
    )

    private val sonarrSettingsGroups: List<SettingsGroup<*>> = listOf(
        SettingsGroup(
            SonarrMediaManagementFeature.PATTERNS,
            sonarrMmBundles,
            sonarrSettingsMmShellContent(provider(sonarrMmBundles)),
        ),
        SettingsGroup(
            SonarrSettingsQualityFeature.PATTERNS,
            sonarrQualityBundles,
            sonarrSettingsQualityShellContent(provider(sonarrQualityBundles)),
        ),
        SettingsGroup(
            SonarrSettingsConnectFeature.PATTERNS,
            sonarrConnectBundles,
            sonarrSettingsConnectShellContent(provider(sonarrConnectBundles)),
        ),
        SettingsGroup(
            SonarrSettingsSystemFeature.PATTERNS,
            sonarrSystemBundles,
            sonarrSettingsSystemShellContent(provider(sonarrSystemBundles)),
        ),
        SettingsGroup(
            SonarrActivityFeature.PATTERNS,
            sonarrActivityBundles,
            sonarrActivityShellContent(provider(sonarrActivityBundles), refreshSignals = queueRefreshSignals),
        ),
    )

    private val lidarrSettingsGroups: List<SettingsGroup<*>> = listOf(
        SettingsGroup(
            LidarrMediaManagementFeature.PATTERNS,
            lidarrMmBundles,
            lidarrSettingsMmShellContent(provider(lidarrMmBundles)),
        ),
        SettingsGroup(
            LidarrSettingsQualityFeature.PATTERNS,
            lidarrQualityBundles,
            lidarrSettingsQualityShellContent(provider(lidarrQualityBundles)),
        ),
        SettingsGroup(
            LidarrSettingsConnectFeature.PATTERNS,
            lidarrConnectBundles,
            lidarrSettingsConnectShellContent(provider(lidarrConnectBundles)),
        ),
        SettingsGroup(
            LidarrSettingsSystemFeature.PATTERNS,
            lidarrSystemBundles,
            lidarrSettingsSystemShellContent(provider(lidarrSystemBundles)),
        ),
        SettingsGroup(
            LidarrActivityFeature.PATTERNS,
            lidarrActivityBundles,
            lidarrActivityShellContent(provider(lidarrActivityBundles), refreshSignals = queueRefreshSignals),
        ),
    )

    /**
     * Every gated bundle, refreshed whenever the server registry changes so a
     * dispatch never waits on a first connect it did not trigger.
     */
    private val allSettingsBundles = listOf(
        radarrMmBundles,
        radarrQualityBundles,
        radarrConnectBundles,
        radarrSystemBundles,
        radarrQueueBundles,
        radarrActivityBundles,
        sonarrMmBundles,
        sonarrQualityBundles,
        sonarrConnectBundles,
        sonarrSystemBundles,
        sonarrActivityBundles,
        lidarrMmBundles,
        lidarrQualityBundles,
        lidarrConnectBundles,
        lidarrSystemBundles,
        lidarrActivityBundles,
    )

    // The `with` wrappers check route.app and fall through to the wrapped
    // content, so the chain serves every Sonarr screen group wired here.
    private val sonarrChained = ArrShellContent()
        .withSonarrSeriesLibrary(SonarrLibraryDeps.from(graph), registry)
        .withSonarrSeriesDetail(SonarrSeriesDetailDeps.from(graph))
        .withSonarrAddNew(SonarrAddDeps.from(graph), registry)
        .withSonarrCalendar(SonarrCalendarDeps.from(graph), registry)
        .withSonarrWanted(SonarrWantedDeps.from(graph), registry)
        .withSonarrSearch(SonarrSearchDeps.from(graph), registry)
        .withSonarrQueue(SonarrQueueDeps.from(graph), registry, refreshSignals = queueRefreshSignals)

    private val lidarrLibrary = lidarrLibraryShellContent(graph)
    private val lidarrAddNew = lidarrAddNewShellContent(graph)
    private val lidarrChained = ArrShellContent(
        library = lidarrLibrary.library,
        add = lidarrAddNew.add,
    )
        .withLidarrArtistDetail(LidarrArtistDetailDeps.from(graph))
        .withLidarrWanted(LidarrWantedDeps.from(graph), registry)
        .withLidarrQueue(LidarrQueueDeps.from(graph), registry, refreshSignals = queueRefreshSignals)
        .withLidarrCalendar(LidarrCalendarDeps.from(graph), registry)
        .withLidarrSearch(LidarrSearchDeps.from(graph), registry)

    private val prowlarrContent = ProwlarrFeatureRoot.shellContent(graph)
    private val bazarrContent = BazarrFeatureRoot.shellContent(graph)

    /**
     * Radarr's built-in queue destination draws a foreign-app placeholder for
     * other apps, so the fan-out only forwards Radarr routes to it. The queue
     * clients ride a settings bundle: this shell slot is outside
     * [SettingsDispatch], so it applies the same connect-then-render gate
     * itself instead of reading the provider before the bundle lands.
     */
    private val radarrQueueSlot: (@Composable (ArrRoute.Queue) -> Unit)? = { route ->
        if (route.app == AppType.RADARR) {
            if (radarrQueueBundles.isReady(route.serverId)) {
                radarrQueueContent.queue?.invoke(route)
            } else {
                LaunchedEffect(route.serverId) {
                    radarrQueueBundles.connectNow(route.serverId)
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    /** The library fan-out; the server-home fallback reuses it once resolved. */
    private val librarySlot = fanOut(
        radarrLibrary.library,
        sonarrChained.library,
        lidarrChained.library,
        prowlarrContent.library,
        bazarrContent.library,
    )

    /** The single merged content the shell renders. */
    val content: ArrShellContent = ArrShellContent(
        serverList = serversContent.serverList,
        serverHome = { route -> ServerHomeHub(route, librarySlot) },
        library = librarySlot,
        detail = fanOut(
            radarrDetail.detail,
            sonarrChained.detail,
            lidarrChained.detail,
            bazarrContent.detail,
        ),
        add = fanOut(radarrAddNew.add, sonarrChained.add, lidarrChained.add),
        queue = fanOut(sonarrChained.queue, lidarrChained.queue, radarrQueueSlot),
        calendar = fanOut(radarrCalendar.calendar, sonarrChained.calendar, lidarrChained.calendar),
        settings = { route -> SettingsLanding(route) },
        feature = { route -> FeatureDispatch(route) },
    )

    init {
        // Every nested pattern must be registered before the shell's NavHost
        // graph is built once per composition; duplicate registrations no-op.
        ServersFeature.register(registry)
        RadarrFeatureRoot.registerRoutes(registry)
        SonarrFeatureRoot.registerRoutes(registry)
        LidarrFeatureRoot.registerRoutes(registry)
        ProwlarrFeatureRoot.registerRoutes(registry)
        BazarrFeatureRoot.registerRoutes(registry)

        scope.launch {
            graph.registry.observe().collect { servers ->
                shellServers.value = servers.map {
                    ShellServer(id = it.id, name = it.name, appType = it.type)
                }
                allSettingsBundles.forEach { it.retainServers(servers) }
            }
        }
    }

    // ---- feature-route dispatch ------------------------------------------

    /**
     * Routes one feature destination to the bundle that owns it. Owner keys
     * are stable per-app prefixes ("radarr", "sonarr", "lidarr",
     * "prowlarr", "bazarr", "servers"), so a prefix lookup survives new
     * screen groups registering their own owner constants.
     */
    @Composable
    private fun FeatureDispatch(route: ArrRoute.Feature) {
        when {
            route.owner == ServersFeature.ROUTE_OWNER -> {
                val slot = serversContent.feature
                if (slot != null) slot.invoke(route) else ShellUnavailable(route)
            }

            route.owner.startsWith("radarr") -> RadarrFeatureDispatch(route)
            route.owner.startsWith("sonarr") -> SonarrFeatureDispatch(route)
            route.owner.startsWith("lidarr") -> LidarrFeatureDispatch(route)

            route.owner.startsWith("prowlarr") -> {
                val slot = prowlarrContent.feature
                if (slot != null) slot.invoke(route) else ShellUnavailable(route)
            }

            route.owner.startsWith("bazarr") -> {
                val slot = bazarrContent.feature
                if (slot != null) slot.invoke(route) else ShellUnavailable(route)
            }

            else -> ShellUnavailable(route)
        }
    }

    /**
     * Radarr feature routes: plain graph-backed groups render directly;
     * queue, activity, and settings groups render once their client bundle
     * is connected, through [SettingsDispatch].
     */
    @Composable
    private fun RadarrFeatureDispatch(route: ArrRoute.Feature) {
        val segments = route.path.split('/')
        val plain = radarrPlainGroups.firstOrNull { (patterns, _) ->
            patterns.any { matchRoutePattern(it, segments) != null }
        }
        if (plain != null) {
            plain.second.feature?.invoke(route)
            return
        }
        SettingsDispatch(route, radarrSettingsGroups)
    }

    @Composable
    private fun SonarrFeatureDispatch(route: ArrRoute.Feature) {
        val segments = route.path.split('/')
        val isSettings = sonarrSettingsGroups.any { group ->
            group.patterns.any { matchRoutePattern(it, segments) != null }
        }
        if (isSettings) {
            SettingsDispatch(route, sonarrSettingsGroups)
            return
        }
        val slot = sonarrChained.feature
        if (slot != null) slot.invoke(route) else ShellUnavailable(route)
    }

    @Composable
    private fun LidarrFeatureDispatch(route: ArrRoute.Feature) {
        val segments = route.path.split('/')
        if (lidarrSettingsGroups.any { group ->
                group.patterns.any { matchRoutePattern(it, segments) != null }
            }) {
            SettingsDispatch(route, lidarrSettingsGroups)
            return
        }
        // Library and add-new keep standalone feature slots; the wrapped
        // chain covers detail, wanted, queue, calendar, and search.
        lidarrLibrary.feature?.invoke(route)
        lidarrAddNew.feature?.invoke(route)
        val slot = lidarrChained.feature
        if (slot != null) slot.invoke(route) else ShellUnavailable(route)
    }

    /**
     * Per-server Retrofit plus raw OkHttp client, mirroring the settings
     * connections: null when the server or its credentials are unusable.
     */
    private suspend fun serverHttp(serverId: ServerId): ServerHttp? {
        val server = graph.registry.byId(serverId) ?: return null
        val endpoint = try {
            ServerEndpoint(
                serverId = server.id,
                baseUrl = server.baseUrl,
                apiKey = graph.secrets.decrypt(server.apiKeyEncrypted),
                basicAuthUser = server.basicAuthUser,
                basicAuthPassword = server.basicAuthPasswordEncrypted?.let { graph.secrets.decrypt(it) },
                proxy = server.proxy,
                trust = server.trust ?: TrustSpec.System,
            )
        } catch (t: Throwable) {
            return null
        }
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val client = clientFactory.clientFor(endpoint)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return ServerHttp(retrofit = retrofit, client = client, baseUrl = baseUrl)
    }

    /**
     * Renders a settings group once its per-server client bundle exists,
     * kicking the connection off on first miss. Every settings pattern
     * carries a {serverId} parameter; the placeholder covers the window
     * before the bundle lands (usually one background tick).
     */
    @Composable
    private fun SettingsDispatch(route: ArrRoute.Feature, groups: List<SettingsGroup<*>>) {
        val segments = route.path.split('/')
        val group = groups.firstOrNull { group ->
            group.patterns.any { matchRoutePattern(it, segments) != null }
        }
        if (group == null) {
            ShellUnavailable(route)
            return
        }
        val serverId = group.patterns.firstNotNullOfOrNull { pattern ->
            matchRoutePattern(pattern, segments)?.get("serverId")
        }?.let(::ServerId)
        if (serverId == null || !group.bundles.isReady(serverId)) {
            if (serverId != null) {
                LaunchedEffect(serverId) { group.bundles.connectNow(serverId) }
            }
            ShellUnavailable(route)
            return
        }
        group.content.feature?.invoke(route)
    }

    // ---- shell destinations ----------------------------------------------

    /**
     * Renders the server-home destination: the hub of everything this app
     * offers for one server. The library, add, queue, and calendar slots are
     * shell routes; wanted, history (activity), and app-specific calendar
     * helpers open feature routes. Without this hub those screens were
     * wired into the shell but reachable by nothing.
     */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun ServerHomeHub(
        route: ArrRoute.ServerHome,
        library: (@Composable (ArrRoute.Library) -> Unit)?,
    ) {
        val servers by shellServers.collectAsStateWithLifecycle()
        val appType = servers.firstOrNull { it.id == route.serverId }?.appType
        if (appType == null) {
            EmptyState(
                title = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_title),
                message = stringResource(
                    dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_message,
                    route.serverId.value,
                ),
            )
            return
        }
        if (appType == AppType.PROWLARR || appType == AppType.BAZARR) {
            // Their roots are single screens (indexers, subtitles); the
            // library slot already is their home content.
            if (library == null) {
                EmptyState(
                    title = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_title),
                    message = stringResource(
                        dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_message,
                        appType.wireName,
                    ),
                )
            } else {
                library(ArrRoute.Library(route.serverId, appType))
            }
            return
        }

        val navigator = LocalArrShellNavigator.current
        val id = route.serverId
        val items: List<Pair<String, () -> Unit>> = listOf(
            stringResource(AppR.string.arr_home_library) to {
                navigator.open(ArrRoute.Library(id, appType))
            },
            stringResource(AppR.string.arr_home_add) to {
                navigator.open(ArrRoute.Add(id, appType))
            },
            stringResource(AppR.string.arr_home_queue) to {
                navigator.open(ArrRoute.Queue(id, appType))
            },
            stringResource(AppR.string.arr_home_calendar) to {
                when (appType) {
                    AppType.RADARR -> RadarrCalendarFeature.openCalendar(navigator, id)
                    AppType.SONARR -> SonarrCalendarRoutes.openCalendar(navigator, id)
                    else -> LidarrCalendarRoutes.openCalendar(navigator, id)
                }
            },
            stringResource(AppR.string.arr_home_wanted) to {
                when (appType) {
                    AppType.RADARR -> RadarrWantedFeature.openWanted(navigator, id)
                    AppType.SONARR -> SonarrWantedRoutes.openWanted(navigator, id)
                    else -> LidarrWantedRoutes.openWanted(navigator, id)
                }
            },
            stringResource(AppR.string.arr_home_history) to {
                when (appType) {
                    AppType.RADARR -> RadarrActivityFeature.openHistory(navigator, id)
                    AppType.SONARR -> SonarrActivityFeature.openHistory(navigator, id)
                    else -> LidarrActivityFeature.openHistory(navigator, id)
                }
            },
        )
        ArrScaffold(title = appType.displayName) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(items) { (title, open) ->
                    ListItem(
                        headlineContent = {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { open() },
                    )
                }
            }
        }
    }

    /**
     * The shell's settings destination: a list of links into the settings
     * groups this wiring gates for the route's app.
     */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsLanding(route: ArrRoute.Settings, modifier: Modifier = Modifier) {
        val navigator = LocalArrShellNavigator.current
        val links = settingsLinks(route)
        ArrScaffold(
            title = stringResource(R.string.arr_bazarr_settings_title),
            modifier = modifier,
        ) { padding ->
            if (links.isEmpty()) {
                EmptyState(
                    title = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_title),
                    message = stringResource(
                        dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_message,
                        route.app.wireName,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
                return@ArrScaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(links) { link ->
                    ListItem(
                        headlineContent = {
                            Text(link.title, style = MaterialTheme.typography.titleSmall)
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navigator.open(link.route) },
                    )
                }
            }
        }
    }

    /** One navigation entry on the settings landing. */
    private data class SettingsLink(val title: String, val route: ArrRoute.Feature)

    /** The settings-group entry links for [route]'s app; empty when none are wireable. */
    @Composable
    private fun settingsLinks(route: ArrRoute.Settings): List<SettingsLink> {
        val id = route.serverId
        val links: List<Pair<String, ArrRoute.Feature?>> = when (route.app) {
            AppType.RADARR -> listOf(
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_mm_title) to
                    RadarrMediaManagementFeature.mediaManagementRoute(id),
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_quality) to
                    RadarrSettingsQualityFeature.qualityProfilesRoute(id),
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_clients_title) to
                    RadarrSettingsConnectFeature.downloadClientsRoute(id),
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_general_title) to
                    RadarrSettingsSystemFeature.generalRoute(id),
            )

            AppType.SONARR -> listOf(
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_mm_title) to
                    SonarrMediaManagementFeature.mediaManagementRoute(id),
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_quality) to
                    SonarrSettingsQualityFeature.qualityProfilesRoute(id),
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_clients_title) to
                    SonarrSettingsConnectFeature.downloadClientsRoute(id),
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_general_title) to
                    SonarrSettingsSystemFeature.generalRoute(id),
            )

            AppType.LIDARR -> listOf(
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_mm_title) to
                    LidarrMediaManagementFeature.mediaManagementRoute(id),
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_quality) to
                    LidarrSettingsQualityFeature.qualityProfilesRoute(id),
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_clients_title) to
                    LidarrSettingsConnectFeature.downloadClientsRoute(id),
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_general_title) to
                    LidarrSettingsSystemFeature.generalRoute(id),
            )

            AppType.PROWLARR -> listOf(
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_clients_title) to
                    ProwlarrSettingsFeature.downloadClientsRoute(id),
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_notifications_title) to
                    ProwlarrSettingsFeature.notificationsRoute(id),
                stringResource(dev.appolon.homelab.arr.ui.R.string.arr_common_general_title) to
                    ProwlarrSettingsFeature.generalRoute(id),
            )

            AppType.BAZARR -> listOf(
                stringResource(R.string.arr_bazarr_settings_title) to
                    fillPattern(BazarrSettingsFeature.HOME_PATTERN, mapOf("serverId" to id.value))
                        ?.let { ArrRoute.Feature(BazarrSettingsFeature.ROUTE_OWNER, it) },
            )
        }
        return links.mapNotNull { (title, target) -> target?.let { SettingsLink(title, it) } }
    }

    // ---- helpers ---------------------------------------------------------

    private fun mmContent(): ArrShellContent =
        radarrSettingsMmShellContent(provider(radarrMmBundles))

    private fun qualityContent(): ArrShellContent =
        radarrSettingsQualityShellContent(provider(radarrQualityBundles))

    private fun connectContent(): ArrShellContent =
        radarrSettingsConnectShellContent(provider(radarrConnectBundles))

    private fun systemContent(): ArrShellContent =
        radarrSettingsSystemShellContent(provider(radarrSystemBundles))

    private fun <T> settingsBundles(connect: suspend (ServerId) -> T?): SettingsBundles<T> =
        SettingsBundles(connect)

    /** Non-null client provider; dispatch gates every call on [SettingsBundles.isReady]. */
    private fun <T> provider(bundles: SettingsBundles<T>): (ServerId) -> T = { id ->
        checkNotNull(bundles.get(id)) { "settings clients must be gated before use" }
    }

    /**
     * Combines app-scoped slots. Every bundled slot renders only for its own
     * app and silently returns for the others, so fanning out composes
     * exactly one screen per route.
     */
    private fun <T : ArrRoute> fanOut(
        vararg slots: (@Composable (T) -> Unit)?,
    ): (@Composable (T) -> Unit)? {
        val live = slots.filterNotNull()
        return if (live.isEmpty()) null else ({ route -> live.forEach { it(route) } })
    }

    /** The shell's own placeholder, reused for groups with no wired content yet. */
    @Composable
    private fun ShellUnavailable(route: ArrRoute.Feature, modifier: Modifier = Modifier) {
        EmptyState(
            title = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_title),
            message = stringResource(
                dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_message,
                route.owner + "/" + route.path,
            ),
            modifier = modifier,
        )
    }

    /**
     * Per-server client bundles for one settings group. The map is snapshot
     * state so a dispatch reading [isReady] recomposes when a bundle lands.
     */
    private class SettingsBundles<T>(private val connect: suspend (ServerId) -> T?) {

        private var map by mutableStateOf<Map<ServerId, T>>(emptyMap())

        fun get(id: ServerId): T? = map[id]

        fun isReady(id: ServerId): Boolean = map.containsKey(id)

        suspend fun connectNow(id: ServerId) {
            connect(id)?.let { value -> map = map + (id to value) }
        }

        /** Drop removed servers. Connections are created by the owning screen. */
        fun retainServers(servers: List<ArrServer>) {
            map = map.filterKeys { id -> servers.any { it.id == id } }
        }
    }

    private class SettingsGroup<T>(
        val patterns: List<String>,
        val bundles: SettingsBundles<T>,
        val content: ArrShellContent,
    )

    /** One server's Retrofit stack plus the raw client the command runner needs. */
    private class ServerHttp(
        val retrofit: Retrofit,
        val client: OkHttpClient,
        val baseUrl: String,
    )

    /**
     * Runs Radarr queue commands over a raw POST instead of the Retrofit
     * `CommandApi`: command payloads put selection fields like `downloadId`,
     * `path`, and `movieIds` at the JSON root, a shape the typed
     * `CommandResource` DTO cannot express. Mirrors the Sonarr queue runner.
     */
    private class OkHttpRadarrQueueCommandRunner(
        client: OkHttpClient,
        baseUrl: String,
    ) : QueueCommandRunner {
        private val runner = ArrCommandRunner(client, baseUrl)

        override suspend fun run(
            name: String,
            downloadId: String?,
            path: String?,
            movieIds: List<Int>?,
        ): ArrResult<Unit> {
            val body = QueueCommandBody(
                name = name,
                downloadId = downloadId,
                path = path,
                movieIds = movieIds,
            ).encode(ArrJson)
            return withContext(Dispatchers.IO) { runner.post("/api/v3/command", body) }
        }
    }
}
