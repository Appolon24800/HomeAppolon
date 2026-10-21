package dev.appolon.homelab.arr.feature.radarr

import androidx.compose.runtime.Composable
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
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
import dev.appolon.homelab.arr.feature.radarr.queue.radarrQueueShellContent
import dev.appolon.homelab.arr.feature.radarr.search.RadarrSearchFeature
import dev.appolon.homelab.arr.feature.radarr.search.radarrSearchShellContent
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrConnectClients
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrSettingsConnectFeature
import dev.appolon.homelab.arr.feature.radarr.settings.connect.radarrSettingsConnectShellContent
import dev.appolon.homelab.arr.feature.radarr.settings.mm.RadarrMediaManagementFeature
import dev.appolon.homelab.arr.feature.radarr.settings.mm.RadarrSettingsMmClients
import dev.appolon.homelab.arr.feature.radarr.settings.mm.radarrSettingsMmShellContent
import dev.appolon.homelab.arr.feature.radarr.settings.quality.RadarrQualityClients
import dev.appolon.homelab.arr.feature.radarr.settings.quality.RadarrSettingsQualityFeature
import dev.appolon.homelab.arr.feature.radarr.settings.quality.radarrSettingsQualityShellContent
import dev.appolon.homelab.arr.feature.radarr.settings.system.RadarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.radarr.settings.system.RadarrSettingsSystemFeature
import dev.appolon.homelab.arr.feature.radarr.settings.system.radarrSettingsSystemShellContent
import dev.appolon.homelab.arr.feature.radarr.wanted.RadarrWantedFeature
import dev.appolon.homelab.arr.feature.radarr.wanted.radarrWantedShellContent
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent

/**
 * Single registration point for every committed Radarr screen group, so the
 * app module wires the whole app with one call per integration concern.
 *
 * Bundled groups: movies library, movie detail, add new with collections,
 * wanted and cutoff, queue with manual import, activity history and
 * blocklist, calendar, search with interactive import, and the media
 * management, quality, connect, and system settings groups. Every feature
 * matrix group for Radarr has a committed home.
 */
object RadarrFeatureRoot {

    /** Registers every group's nested routes; each group registers once. */
    fun registerRoutes(registry: ArrRouteRegistry): Unit {
        RadarrLibraryFeature.register(registry)
        RadarrDetailFeature.register(registry)
        RadarrAddNewFeature.register(registry)
        RadarrWantedFeature.register(registry)
        RadarrQueueFeature.register(registry)
        RadarrActivityFeature.register(registry)
        RadarrCalendarFeature.register(registry)
        RadarrSearchFeature.register(registry)
        RadarrMediaManagementFeature.register(registry)
        RadarrSettingsQualityFeature.register(registry)
        RadarrSettingsConnectFeature.register(registry)
        RadarrSettingsSystemFeature.register(registry)
    }

    /**
     * Merged shell content for every bundled group. The graph-backed groups
     * resolve their own connections from [graph]; the queue, activity, and
     * settings groups take pre-built per-server client bundles because those
     * groups own Retrofit-driven repositories the data graph cannot supply.
     * Call once at the composition root, after [registerRoutes].
     */
    fun shellContent(
        graph: ArrDataGraph,
        queueClients: (ServerId) -> RadarrQueueClients,
        activityClients: (ServerId) -> RadarrActivityClients,
        mediaManagementClients: (ServerId) -> RadarrSettingsMmClients,
        qualityClients: (ServerId) -> RadarrQualityClients,
        connectClients: (ServerId) -> RadarrConnectClients,
        systemClients: (ServerId) -> RadarrSettingsSystemClients,
    ): ArrShellContent {
        val library = radarrLibraryShellContent(graph)
        val detail = radarrDetailShellContent(graph)
        val addNew = radarrAddNewShellContent(graph)
        val wanted = radarrWantedShellContent(graph)
        val calendar = radarrCalendarShellContent(graph)
        val search = radarrSearchShellContent(graph)
        val queue = radarrQueueShellContent(queueClients)
        val activity = radarrActivityShellContent(activityClients)
        val mediaManagement = radarrSettingsMmShellContent(mediaManagementClients)
        val quality = radarrSettingsQualityShellContent(qualityClients)
        val connect = radarrSettingsConnectShellContent(connectClients)
        val system = radarrSettingsSystemShellContent(systemClients)
        return ArrShellContent(
            library = library.library,
            detail = detail.detail,
            add = addNew.add,
            queue = queue.queue,
            calendar = calendar.calendar,
            feature = featureSlot(
                RadarrLibraryFeature.PATTERNS to library,
                RadarrDetailFeature.PATTERNS to detail,
                RadarrAddNewFeature.PATTERNS to addNew,
                RadarrWantedFeature.PATTERNS to wanted,
                RadarrCalendarFeature.PATTERNS to calendar,
                RadarrSearchFeature.PATTERNS to search,
                RadarrQueueFeature.PATTERNS to queue,
                RadarrActivityFeature.PATTERNS to activity,
                RadarrMediaManagementFeature.PATTERNS to mediaManagement,
                RadarrSettingsQualityFeature.PATTERNS to quality,
                RadarrSettingsConnectFeature.PATTERNS to connect,
                RadarrSettingsSystemFeature.PATTERNS to system,
            ),
        )
    }

    /** The app this bundle wires. */
    fun appType(): AppType = AppType.RADARR
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
