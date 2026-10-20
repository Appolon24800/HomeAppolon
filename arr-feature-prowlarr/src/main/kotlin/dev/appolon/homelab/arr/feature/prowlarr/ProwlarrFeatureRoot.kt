package dev.appolon.homelab.arr.feature.prowlarr

import androidx.compose.runtime.Composable
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.prowlarr.history.ProwlarrHistoryFeature
import dev.appolon.homelab.arr.feature.prowlarr.history.prowlarrHistoryShellContent
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ProwlarrIndexersFeature
import dev.appolon.homelab.arr.feature.prowlarr.indexers.prowlarrIndexersShellContent
import dev.appolon.homelab.arr.feature.prowlarr.stats.ProwlarrStatsFeature
import dev.appolon.homelab.arr.feature.prowlarr.stats.prowlarrStatsShellContent
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent

/**
 * Single registration point for every committed Prowlarr screen group, so
 * the app module wires the whole app with one call per integration concern.
 *
 * Bundled groups: indexers (filling the shell's library slot for Prowlarr),
 * stats, and the query history log.
 *
 * Skipped: the search and settings groups have no committed Prowlarr files
 * yet.
 */
object ProwlarrFeatureRoot {

    /** Registers every group's nested routes; each group registers once. */
    fun registerRoutes(registry: ArrRouteRegistry): Unit {
        ProwlarrIndexersFeature.register(registry)
        ProwlarrStatsFeature.register(registry)
        ProwlarrHistoryFeature.register(registry)
    }

    /**
     * Merged shell content for every bundled group. Both groups resolve
     * their own connections from [graph]. Call once at the composition root,
     * after [registerRoutes].
     */
    fun shellContent(graph: ArrDataGraph): ArrShellContent {
        val indexers = prowlarrIndexersShellContent(graph)
        val stats = prowlarrStatsShellContent(graph)
        val history = prowlarrHistoryShellContent(graph)
        return ArrShellContent(
            library = indexers.library,
            feature = featureSlot(
                ProwlarrIndexersFeature.PATTERNS to indexers,
                ProwlarrStatsFeature.PATTERNS to stats,
                ProwlarrHistoryFeature.PATTERNS to history,
            ),
        )
    }

    /** The app this bundle wires. */
    fun appType(): AppType = AppType.PROWLARR
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
