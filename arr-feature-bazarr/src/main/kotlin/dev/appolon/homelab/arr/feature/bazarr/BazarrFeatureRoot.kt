package dev.appolon.homelab.arr.feature.bazarr

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.bazarr.episodes.BazarrEpisodesFeature
import dev.appolon.homelab.arr.feature.bazarr.episodes.withBazarrEpisodes
import dev.appolon.homelab.arr.feature.bazarr.history.BazarrHistoryFeature
import dev.appolon.homelab.arr.feature.bazarr.history.withBazarrHistory
import dev.appolon.homelab.arr.feature.bazarr.series.BazarrLibraryDestination
import dev.appolon.homelab.arr.feature.bazarr.series.BazarrLibraryFeature
import dev.appolon.homelab.arr.feature.bazarr.series.BazarrLibraryFeatureSlot
import dev.appolon.homelab.arr.feature.bazarr.series.BazarrNavSlots
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMediaKind
import dev.appolon.homelab.arr.feature.bazarr.settings.BazarrSettingsFeature
import dev.appolon.homelab.arr.feature.bazarr.settings.withBazarrSettings
import dev.appolon.homelab.arr.feature.bazarr.wanted.BazarrWantedFeature
import dev.appolon.homelab.arr.feature.bazarr.wanted.withBazarrWanted
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Single registration point for every committed Bazarr screen group, so the
 * app module wires the whole app with one call per integration concern.
 *
 * Bundled groups: the series and movies lists with the series episodes
 * browser, episode and movie subtitles, the dedicated wanted and history
 * and blacklist groups, and the settings group. Every feature matrix group
 * for Bazarr has a committed home.
 */
object BazarrFeatureRoot {

    /** Registers every group's nested routes; each group registers once. */
    fun registerRoutes(registry: ArrRouteRegistry): Unit {
        BazarrLibraryFeature.register(registry)
        BazarrEpisodesFeature.register(registry)
        BazarrWantedFeature.register(registry)
        BazarrHistoryFeature.register(registry)
        BazarrSettingsFeature.register(registry)
    }

    /**
     * Shell content for every bundled group. The series group fills the
     * library slot; the episodes, wanted, history, and settings groups wrap
     * the feature slot through their `with` builders, each rendering only
     * the routes of its own owner. Every group resolves its own connections
     * from [graph]. Call once at the composition root, after
     * [registerRoutes].
     */
    fun shellContent(graph: ArrDataGraph): ArrShellContent =
        bazarrSeriesContent(graph)
            .withBazarrEpisodes(graph)
            .withBazarrWanted(graph)
            .withBazarrHistory(graph)
            .withBazarrSettings(graph)

    /** The app this bundle wires. */
    fun appType(): AppType = AppType.BAZARR
}

/**
 * The series group's contribution: the library slot plus its owner-gated
 * feature routes. The group hands subtitle navigation to the integration
 * layer through [BazarrNavSlots], whose callbacks need the shell navigator;
 * that is only readable during composition, so the slots resolve it there
 * and forward to the episodes group's open helpers. Episode activation
 * lands on the series' subtitle list, the narrowest route the episodes
 * group exposes.
 */
private fun bazarrSeriesContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    library = { route ->
        if (route.app == AppType.BAZARR) {
            BazarrLibraryDestination(
                serverId = route.serverId,
                initialKind = BazarrMediaKind.SERIES,
                graph = graph,
                navSlots = subtitleNavSlots(LocalArrShellNavigator.current),
            )
        }
    },
    feature = { route ->
        BazarrLibraryFeatureSlot(route, graph, subtitleNavSlots(LocalArrShellNavigator.current))
    },
)

/** Connects the series group's handoffs to the episodes group's routes. */
private fun subtitleNavSlots(navigator: ArrShellNavigator): BazarrNavSlots = BazarrNavSlots(
    onOpenEpisodeSubtitles = { serverId, seriesId, _ ->
        BazarrEpisodesFeature.openSeries(navigator, serverId, seriesId)
    },
    onOpenMovieSubtitles = { serverId, radarrId ->
        BazarrEpisodesFeature.openMovie(navigator, serverId, radarrId)
    },
)
