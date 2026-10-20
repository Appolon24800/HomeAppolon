package dev.appolon.homelab.arr.feature.bazarr

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.bazarr.episodes.BazarrEpisodesFeature
import dev.appolon.homelab.arr.feature.bazarr.episodes.withBazarrEpisodes
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.shell.ArrShellContent

/**
 * Single registration point for every committed Bazarr screen group, so the
 * app module wires the whole app with one call per integration concern.
 *
 * Bundled groups: episodes and movie subtitle listing, whose patterns also
 * cover the wanted and history screens.
 *
 * Skipped: the dedicated series and movies list group and the settings
 * group have no committed Bazarr files yet.
 */
object BazarrFeatureRoot {

    /** Registers every group's nested routes; each group registers once. */
    fun registerRoutes(registry: ArrRouteRegistry): Unit {
        BazarrEpisodesFeature.register(registry)
    }

    /**
     * Shell content for every bundled group. The episodes group resolves its
     * own connections from [graph] and wraps any previous content through
     * its `with` builder, so no merging is needed here. Call once at the
     * composition root, after [registerRoutes].
     */
    fun shellContent(graph: ArrDataGraph): ArrShellContent =
        ArrShellContent().withBazarrEpisodes(graph)

    /** The app this bundle wires. */
    fun appType(): AppType = AppType.BAZARR
}
