package dev.appolon.homelab.arr.feature.sonarr.addnew.data

import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource

/**
 * Season-level defaults for the add sheet's season picker. A monitor strategy
 * chooses which season checkboxes start checked; episode-level strategies
 * (future, missing, existing, recent) keep every season checked because they
 * only change which episodes Sonarr monitors inside a season, while season
 * strategies narrow the checked set. Users can diverge freely; the explicit
 * season flags travel in the POST body alongside the strategy.
 */
object SeasonMonitoring {

    /** Season numbers a lookup result reports, specials (0) included. */
    fun seasonNumbersOf(series: SeriesResource): List<Int> =
        series.seasons.orEmpty().map { it.seasonNumber }

    /** Seasons checked by default for [strategy], given the available [seasonNumbers]. */
    fun defaultMonitored(strategy: MonitorTypes, seasonNumbers: List<Int>): Set<Int> {
        val regular = seasonNumbers.filter { it > 0 }
        return when (strategy) {
            MonitorTypes.UNKNOWN,
            MonitorTypes.ALL,
            MonitorTypes.FUTURE,
            MonitorTypes.MISSING,
            MonitorTypes.EXISTING,
            MonitorTypes.RECENT,
            MonitorTypes.MONITORSPECIALS,
            MonitorTypes.SKIP,
            -> seasonNumbers.toSet()

            MonitorTypes.FIRSTSEASON,
            MonitorTypes.PILOT,
            -> setOfNotNull(regular.minOrNull())

            MonitorTypes.LASTSEASON,
            MonitorTypes.LATESTSEASON,
            -> setOfNotNull(regular.maxOrNull())

            MonitorTypes.UNMONITORSPECIALS -> regular.toSet()
            MonitorTypes.NONE -> emptySet()
        }
    }
}
