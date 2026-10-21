package dev.appolon.homelab.arr.feature.lidarr

import androidx.compose.runtime.Composable
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.lidarr.activity.LidarrActivityClients
import dev.appolon.homelab.arr.feature.lidarr.activity.LidarrActivityFeature
import dev.appolon.homelab.arr.feature.lidarr.activity.lidarrActivityShellContent
import dev.appolon.homelab.arr.feature.lidarr.addnew.LidarrAddNewFeature
import dev.appolon.homelab.arr.feature.lidarr.addnew.lidarrAddNewShellContent
import dev.appolon.homelab.arr.feature.lidarr.calendar.LidarrCalendarDeps
import dev.appolon.homelab.arr.feature.lidarr.calendar.LidarrCalendarRoutes
import dev.appolon.homelab.arr.feature.lidarr.calendar.withLidarrCalendar
import dev.appolon.homelab.arr.feature.lidarr.detail.LidarrArtistDetailDeps
import dev.appolon.homelab.arr.feature.lidarr.detail.withLidarrArtistDetail
import dev.appolon.homelab.arr.feature.lidarr.library.LidarrLibraryFeature
import dev.appolon.homelab.arr.feature.lidarr.library.lidarrLibraryShellContent
import dev.appolon.homelab.arr.feature.lidarr.queue.LidarrQueueClients
import dev.appolon.homelab.arr.feature.lidarr.queue.LidarrQueueFeature
import dev.appolon.homelab.arr.feature.lidarr.queue.lidarrQueueShellContent
import dev.appolon.homelab.arr.feature.lidarr.search.LidarrSearchDeps
import dev.appolon.homelab.arr.feature.lidarr.search.LidarrSearchFeature
import dev.appolon.homelab.arr.feature.lidarr.search.withLidarrSearch
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.LidarrConnectClients
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.LidarrSettingsConnectFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.lidarrSettingsConnectShellContent
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.LidarrMediaManagementFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.LidarrSettingsMmClients
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.lidarrSettingsMmShellContent
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.LidarrQualityClients
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.LidarrSettingsQualityFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.lidarrSettingsQualityShellContent
import dev.appolon.homelab.arr.feature.lidarr.settings.system.LidarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.lidarr.settings.system.LidarrSettingsSystemFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.system.lidarrSettingsSystemShellContent
import dev.appolon.homelab.arr.feature.lidarr.wanted.LidarrWantedDeps
import dev.appolon.homelab.arr.feature.lidarr.wanted.LidarrWantedRoutes
import dev.appolon.homelab.arr.feature.lidarr.wanted.withLidarrWanted
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent

/**
 * Single registration point for every committed Lidarr screen group, so the
 * app module wires the whole app with one call per integration concern.
 *
 * Bundled groups: artists library, artist detail, add new, wanted, queue
 * with manual import, activity history and blocklist, calendar with the
 * day deep link, interactive search and import, and the media management,
 * quality, connect, and system settings groups. Every feature matrix group
 * for Lidarr has a committed home.
 */
object LidarrFeatureRoot {

    /**
     * Registers every group's nested routes; each group registers once. The
     * wanted, calendar, and search groups' shell builders also register
     * their patterns, and the registry deduplicates, so wiring content
     * after this call is safe.
     */
    fun registerRoutes(registry: ArrRouteRegistry): Unit {
        LidarrLibraryFeature.register(registry)
        LidarrAddNewFeature.register(registry)
        LidarrQueueFeature.register(registry)
        LidarrWantedRoutes.register(registry)
        LidarrCalendarRoutes.register(registry)
        LidarrSearchFeature.register(registry)
        LidarrActivityFeature.register(registry)
        LidarrMediaManagementFeature.register(registry)
        LidarrSettingsQualityFeature.register(registry)
        LidarrSettingsConnectFeature.register(registry)
        LidarrSettingsSystemFeature.register(registry)
    }

    /**
     * Merged shell content for every bundled group. The artist detail,
     * wanted, calendar, and search groups chain through their own `with`
     * wrappers; library, add new, queue, activity, and the settings groups
     * take the graph or pre-built per-server client bundles because those
     * groups own Retrofit-driven repositories the data graph cannot
     * supply. Call once at the composition root, after [registerRoutes].
     */
    fun shellContent(
        registry: ArrRouteRegistry,
        graph: ArrDataGraph,
        queueClients: (ServerId) -> LidarrQueueClients,
        activityClients: (ServerId) -> LidarrActivityClients,
        mediaManagementClients: (ServerId) -> LidarrSettingsMmClients,
        qualityClients: (ServerId) -> LidarrQualityClients,
        connectClients: (ServerId) -> LidarrConnectClients,
        systemClients: (ServerId) -> LidarrSettingsSystemClients,
    ): ArrShellContent {
        val graphBacked = ArrShellContent()
            .withLidarrArtistDetail(LidarrArtistDetailDeps.from(graph))
            .withLidarrWanted(LidarrWantedDeps.from(graph), registry)
            .withLidarrCalendar(LidarrCalendarDeps.from(graph), registry)
            .withLidarrSearch(LidarrSearchDeps.from(graph), registry)
        val library = lidarrLibraryShellContent(graph)
        val addNew = lidarrAddNewShellContent(graph)
        val queue = lidarrQueueShellContent(queueClients)
        val activity = lidarrActivityShellContent(activityClients)
        val mediaManagement = lidarrSettingsMmShellContent(mediaManagementClients)
        val quality = lidarrSettingsQualityShellContent(qualityClients)
        val connect = lidarrSettingsConnectShellContent(connectClients)
        val system = lidarrSettingsSystemShellContent(systemClients)
        return ArrShellContent(
            serverList = graphBacked.serverList,
            serverHome = graphBacked.serverHome,
            library = library.library,
            detail = graphBacked.detail,
            add = addNew.add,
            queue = queue.queue,
            calendar = graphBacked.calendar,
            settings = graphBacked.settings,
            feature = featureSlot(
                LidarrLibraryFeature.PATTERNS to library,
                LidarrAddNewFeature.PATTERNS to addNew,
                LidarrWantedRoutes.PATTERNS to graphBacked,
                LidarrCalendarRoutes.PATTERNS to graphBacked,
                LidarrSearchFeature.PATTERNS to graphBacked,
                LidarrQueueFeature.PATTERNS to queue,
                LidarrActivityFeature.PATTERNS to activity,
                LidarrMediaManagementFeature.PATTERNS to mediaManagement,
                LidarrSettingsQualityFeature.PATTERNS to quality,
                LidarrSettingsConnectFeature.PATTERNS to connect,
                LidarrSettingsSystemFeature.PATTERNS to system,
            ),
        )
    }

    /** The app this bundle wires. */
    fun appType(): AppType = AppType.LIDARR
}

/**
 * Builds one feature slot from pattern-keyed group contents. Group slots
 * render an empty state for routes they do not own, so stacking them would
 * draw twice; dispatching on the registered patterns renders exactly the
 * owning group. Unmatched routes cannot occur because every registered
 * pattern belongs to a bundled group.
 */
private fun featureSlot(
    vararg groups: Pair<List<String>, ArrShellContent>,
): (@Composable (ArrRoute.Feature) -> Unit)? = { route ->
    val segments = route.path.split('/')
    groups.firstOrNull { (patterns, _) ->
        patterns.any { matchRoutePattern(it, segments) != null }
    }?.second?.feature?.invoke(route)
}
