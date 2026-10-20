package dev.appolon.homelab.arr.feature.sonarr

import androidx.compose.runtime.Composable
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.sonarr.activity.SonarrActivityClients
import dev.appolon.homelab.arr.feature.sonarr.activity.SonarrActivityFeature
import dev.appolon.homelab.arr.feature.sonarr.activity.sonarrActivityShellContent
import dev.appolon.homelab.arr.feature.sonarr.addnew.SonarrAddDeps
import dev.appolon.homelab.arr.feature.sonarr.addnew.SonarrAddRoutes
import dev.appolon.homelab.arr.feature.sonarr.addnew.withSonarrAddNew
import dev.appolon.homelab.arr.feature.sonarr.calendar.SonarrCalendarDeps
import dev.appolon.homelab.arr.feature.sonarr.calendar.SonarrCalendarRoutes
import dev.appolon.homelab.arr.feature.sonarr.calendar.withSonarrCalendar
import dev.appolon.homelab.arr.feature.sonarr.detail.SonarrSeriesDetailDeps
import dev.appolon.homelab.arr.feature.sonarr.detail.withSonarrSeriesDetail
import dev.appolon.homelab.arr.feature.sonarr.library.SonarrLibraryDeps
import dev.appolon.homelab.arr.feature.sonarr.library.SonarrLibraryRoutes
import dev.appolon.homelab.arr.feature.sonarr.library.withSonarrSeriesLibrary
import dev.appolon.homelab.arr.feature.sonarr.queue.SonarrQueueClients
import dev.appolon.homelab.arr.feature.sonarr.queue.SonarrQueueFeature
import dev.appolon.homelab.arr.feature.sonarr.queue.sonarrQueueShellContent
import dev.appolon.homelab.arr.feature.sonarr.search.SonarrSearchDeps
import dev.appolon.homelab.arr.feature.sonarr.search.SonarrSearchFeature
import dev.appolon.homelab.arr.feature.sonarr.search.withSonarrSearch
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.SonarrConnectClients
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.SonarrSettingsConnectFeature
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.sonarrSettingsConnectShellContent
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.SonarrMediaManagementFeature
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.SonarrSettingsMmClients
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.sonarrSettingsMmShellContent
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.SonarrQualityClients
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.SonarrSettingsQualityFeature
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.sonarrSettingsQualityShellContent
import dev.appolon.homelab.arr.feature.sonarr.wanted.SonarrWantedDeps
import dev.appolon.homelab.arr.feature.sonarr.wanted.SonarrWantedRoutes
import dev.appolon.homelab.arr.feature.sonarr.wanted.withSonarrWanted
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent

/**
 * Single registration point for every committed Sonarr screen group, so the
 * app module wires the whole app with one call per integration concern.
 *
 * Bundled groups: series library, series detail, add new, wanted with season
 * pass, queue with manual import, activity history and blocklist, calendar,
 * search with interactive import, and the media management, quality, and
 * connect settings groups.
 *
 * Skipped: the settings-system group has no committed Sonarr files yet.
 */
object SonarrFeatureRoot {

    /**
     * Registers every group's nested routes; each group registers once. The
     * graph-backed groups' shell builders also register their patterns, and
     * the registry deduplicates, so wiring content after this call is safe.
     */
    fun registerRoutes(registry: ArrRouteRegistry): Unit {
        SonarrLibraryRoutes.register(registry)
        SonarrAddRoutes.register(registry)
        SonarrQueueFeature.register(registry)
        SonarrCalendarRoutes.register(registry)
        SonarrWantedRoutes.register(registry)
        SonarrMediaManagementFeature.register(registry)
        SonarrSearchFeature.register(registry)
        SonarrSettingsQualityFeature.register(registry)
        SonarrActivityFeature.register(registry)
        SonarrSettingsConnectFeature.register(registry)
    }

    /**
     * Merged shell content for every bundled group. The graph-backed groups
     * chain through their own `with` wrappers; the queue, activity, and
     * settings groups take pre-built per-server client bundles because those
     * groups own Retrofit-driven repositories the data graph cannot supply.
     * Call once at the composition root, after [registerRoutes].
     */
    fun shellContent(
        registry: ArrRouteRegistry,
        graph: ArrDataGraph,
        queueClients: (ServerId) -> SonarrQueueClients,
        activityClients: (ServerId) -> SonarrActivityClients,
        mediaManagementClients: (ServerId) -> SonarrSettingsMmClients,
        qualityClients: (ServerId) -> SonarrQualityClients,
        connectClients: (ServerId) -> SonarrConnectClients,
    ): ArrShellContent {
        val graphBacked = ArrShellContent()
            .withSonarrSeriesLibrary(SonarrLibraryDeps.from(graph), registry)
            .withSonarrSeriesDetail(SonarrSeriesDetailDeps.from(graph))
            .withSonarrAddNew(SonarrAddDeps.from(graph), registry)
            .withSonarrCalendar(SonarrCalendarDeps.from(graph), registry)
            .withSonarrWanted(SonarrWantedDeps.from(graph), registry)
            .withSonarrSearch(SonarrSearchDeps.from(graph), registry)
        val queue = sonarrQueueShellContent(queueClients)
        val activity = sonarrActivityShellContent(activityClients)
        val mediaManagement = sonarrSettingsMmShellContent(mediaManagementClients)
        val quality = sonarrSettingsQualityShellContent(qualityClients)
        val connect = sonarrSettingsConnectShellContent(connectClients)
        return ArrShellContent(
            serverList = graphBacked.serverList,
            serverHome = graphBacked.serverHome,
            library = graphBacked.library,
            detail = graphBacked.detail,
            add = graphBacked.add,
            queue = queue.queue,
            calendar = graphBacked.calendar,
            settings = graphBacked.settings,
            feature = featureSlot(
                graphBackedPatterns() to graphBacked,
                SonarrQueueFeature.PATTERNS to queue,
                SonarrActivityFeature.PATTERNS to activity,
                SonarrMediaManagementFeature.PATTERNS to mediaManagement,
                SonarrSettingsQualityFeature.PATTERNS to quality,
                SonarrSettingsConnectFeature.PATTERNS to connect,
            ),
        )
    }

    /** The app this bundle wires. */
    fun appType(): AppType = AppType.SONARR
}

/** Nested patterns owned by the graph-backed `with` chain above. */
private fun graphBackedPatterns(): List<String> =
    listOf(SonarrLibraryRoutes.LIBRARY_PATTERN, SonarrAddRoutes.ADD_PATTERN) +
        SonarrCalendarRoutes.PATTERNS +
        SonarrWantedRoutes.PATTERNS +
        SonarrSearchFeature.PATTERNS

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
