package dev.appolon.homelab.arr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.feature.bazarr.BazarrFeatureRoot
import dev.appolon.homelab.arr.feature.lidarr.LidarrFeatureRoot
import dev.appolon.homelab.arr.feature.lidarr.addnew.LidarrAddNewFeature
import dev.appolon.homelab.arr.feature.lidarr.addnew.lidarrAddNewShellContent
import dev.appolon.homelab.arr.feature.lidarr.detail.LidarrArtistDetailDeps
import dev.appolon.homelab.arr.feature.lidarr.detail.withLidarrArtistDetail
import dev.appolon.homelab.arr.feature.lidarr.library.LidarrLibraryFeature
import dev.appolon.homelab.arr.feature.lidarr.library.lidarrLibraryShellContent
import dev.appolon.homelab.arr.feature.lidarr.queue.LidarrQueueDeps
import dev.appolon.homelab.arr.feature.lidarr.queue.withLidarrQueue
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.LidarrMediaManagementFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.LidarrSettingsMmClients
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.LidarrSettingsMmConnection
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.lidarrSettingsMmShellContent
import dev.appolon.homelab.arr.feature.lidarr.wanted.LidarrWantedDeps
import dev.appolon.homelab.arr.feature.lidarr.wanted.withLidarrWanted
import dev.appolon.homelab.arr.feature.prowlarr.ProwlarrFeatureRoot
import dev.appolon.homelab.arr.feature.radarr.RadarrFeatureRoot
import dev.appolon.homelab.arr.feature.radarr.addnew.RadarrAddNewFeature
import dev.appolon.homelab.arr.feature.radarr.addnew.radarrAddNewShellContent
import dev.appolon.homelab.arr.feature.radarr.calendar.RadarrCalendarFeature
import dev.appolon.homelab.arr.feature.radarr.calendar.radarrCalendarShellContent
import dev.appolon.homelab.arr.feature.radarr.detail.RadarrDetailFeature
import dev.appolon.homelab.arr.feature.radarr.detail.radarrDetailShellContent
import dev.appolon.homelab.arr.feature.radarr.library.RadarrLibraryFeature
import dev.appolon.homelab.arr.feature.radarr.library.radarrLibraryShellContent
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
import dev.appolon.homelab.arr.feature.sonarr.activity.SonarrActivityFeature
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
import dev.appolon.homelab.arr.feature.sonarr.wanted.SonarrWantedDeps
import dev.appolon.homelab.arr.feature.sonarr.wanted.withSonarrWanted
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.servers.ServersFeature
import dev.appolon.homelab.arr.servers.serversShellContent
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ShellServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Builds the one [ArrShellContent] the dashboard's arr host renders, by
 * merging every feature bundle that is wireable from :app's dependency set.
 *
 * What :app can wire is bounded by the integration rules: project
 * dependencies only, so groups whose client bundles would require Retrofit
 * construction in the composition root are left to the shell's "not
 * available" placeholder and reported to the orchestrator. Graph-backed
 * groups (library, detail, add, calendar, wanted, search, queue for Sonarr
 * and Lidarr) connect internally and need nothing from here. Settings groups
 * expose suspend connections plus synchronous client providers, so their
 * bundles are pre-connected per server and gated at dispatch time.
 */
internal class ArrFeatureWiring(
    private val graph: ArrDataGraph,
    registry: ArrRouteRegistry,
    clientFactory: ArrClientFactory,
    scope: CoroutineScope,
) {
    /** Switcher display data for [dev.appolon.homelab.arr.ArrHost]. */
    val shellServers = MutableStateFlow<List<ShellServer>>(emptyList())

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

    private val lidarrMmBundles = settingsBundles { id ->
        LidarrSettingsMmConnection(graph.registry, graph.secrets, clientFactory).connect(id)?.let { apis ->
            LidarrSettingsMmClients(graph.entityStore, graph.actionQueue, apis)
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
    )

    private val lidarrSettingsGroups: List<SettingsGroup<*>> = listOf(
        SettingsGroup(
            LidarrMediaManagementFeature.PATTERNS,
            lidarrMmBundles,
            lidarrSettingsMmShellContent(provider(lidarrMmBundles)),
        ),
    )

    private val allSettingsBundles = listOf(
        radarrMmBundles,
        radarrQualityBundles,
        radarrConnectBundles,
        radarrSystemBundles,
        sonarrMmBundles,
        sonarrQualityBundles,
        sonarrConnectBundles,
        lidarrMmBundles,
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
        .withSonarrQueue(SonarrQueueDeps.from(graph), registry)

    private val lidarrLibrary = lidarrLibraryShellContent(graph)
    private val lidarrAddNew = lidarrAddNewShellContent(graph)
    private val lidarrChained = ArrShellContent(
        library = lidarrLibrary.library,
        add = lidarrAddNew.add,
    )
        .withLidarrArtistDetail(LidarrArtistDetailDeps.from(graph))
        .withLidarrWanted(LidarrWantedDeps.from(graph), registry)
        .withLidarrQueue(LidarrQueueDeps.from(graph), registry)

    private val prowlarrContent = ProwlarrFeatureRoot.shellContent(graph)
    private val bazarrContent = BazarrFeatureRoot.shellContent(graph)

    /** The single merged content the shell renders. */
    val content: ArrShellContent = ArrShellContent(
        serverList = serversContent.serverList,
        serverHome = sonarrChained.serverHome,
        library = fanOut(
            radarrLibrary.library,
            sonarrChained.library,
            lidarrChained.library,
            prowlarrContent.library,
            bazarrContent.library,
        ),
        detail = fanOut(
            radarrDetail.detail,
            sonarrChained.detail,
            lidarrChained.detail,
            bazarrContent.detail,
        ),
        add = fanOut(radarrAddNew.add, sonarrChained.add, lidarrChained.add),
        queue = fanOut(sonarrChained.queue, lidarrChained.queue),
        calendar = fanOut(radarrCalendar.calendar, sonarrChained.calendar),
        settings = sonarrChained.settings,
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
                allSettingsBundles.forEach { it.refresh(servers) }
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
     * Radarr feature routes: plain graph-backed groups render directly,
     * settings groups render once their client bundle is connected. Queue
     * and activity routes currently fall through to the placeholder because
     * no in-module connection builds those client bundles yet.
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
        // The activity group has no committed connection helper yet.
        if (SonarrActivityFeature.PATTERNS.any { matchRoutePattern(it, segments) != null }) {
            ShellUnavailable(route)
            return
        }
        val slot = sonarrChained.feature
        if (slot != null) slot.invoke(route) else ShellUnavailable(route)
    }

    @Composable
    private fun LidarrFeatureDispatch(route: ArrRoute.Feature) {
        val segments = route.path.split('/')
        if (LidarrMediaManagementFeature.PATTERNS.any { matchRoutePattern(it, segments) != null }) {
            SettingsDispatch(route, lidarrSettingsGroups)
            return
        }
        // Library and add-new keep standalone feature slots; the wrapped
        // chain covers wanted, queue, and any future wrappers above them.
        lidarrLibrary.feature?.invoke(route)
        lidarrAddNew.feature?.invoke(route)
        val slot = lidarrChained.feature
        if (slot != null) slot.invoke(route) else ShellUnavailable(route)
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

        /** Reconnects every listed server and drops bundles of removed ones. */
        suspend fun refresh(servers: List<ArrServer>) {
            servers.forEach { server ->
                connect(server.id)?.let { value -> map = map + (server.id to value) }
            }
            map = map.filterKeys { id -> servers.any { it.id == id } }
        }
    }

    private class SettingsGroup<T>(
        val patterns: List<String>,
        val bundles: SettingsBundles<T>,
        val content: ArrShellContent,
    )
}
