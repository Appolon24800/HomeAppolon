package dev.appolon.homelab.arr

import dev.appolon.homelab.R as AppR
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.bazarr.history.BazarrHistoryFeature
import dev.appolon.homelab.arr.feature.bazarr.series.BazarrLibraryFeature
import dev.appolon.homelab.arr.feature.bazarr.wanted.BazarrWantedFeature
import dev.appolon.homelab.arr.feature.prowlarr.history.ProwlarrHistoryFeature
import dev.appolon.homelab.arr.feature.prowlarr.search.ProwlarrSearchFeature
import dev.appolon.homelab.arr.feature.prowlarr.search.SearchPage
import dev.appolon.homelab.arr.feature.prowlarr.stats.ProwlarrStatsFeature
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.R as UiR

internal data class ServerHomeEntry(val title: Int, val route: ArrRoute)

/** Entry menus for apps whose library does not link their other feature groups. */
internal fun additionalServerHomeEntries(app: AppType, id: ServerId): List<ServerHomeEntry> {
    fun feature(owner: String, pattern: String) = ArrRoute.Feature(
        owner,
        checkNotNull(fillPattern(pattern, mapOf("serverId" to id.value))),
    )
    return when (app) {
        AppType.PROWLARR -> listOf(
            ServerHomeEntry(UiR.string.arr_common_indexers_title, ArrRoute.Library(id, app)),
            ServerHomeEntry(AppR.string.arr_home_search, checkNotNull(ProwlarrSearchFeature.route(id))),
            ServerHomeEntry(AppR.string.arr_home_search_history, checkNotNull(ProwlarrSearchFeature.route(id, SearchPage.HISTORY))),
            ServerHomeEntry(AppR.string.arr_home_search_stats, checkNotNull(ProwlarrSearchFeature.route(id, SearchPage.STATS))),
            ServerHomeEntry(UiR.string.arr_common_history_title, feature(ProwlarrHistoryFeature.ROUTE_OWNER, ProwlarrHistoryFeature.HISTORY_PATTERN)),
            ServerHomeEntry(dev.appolon.homelab.arr.feature.prowlarr.R.string.arr_prowlarr_stats_title, feature(ProwlarrStatsFeature.ROUTE_OWNER, ProwlarrStatsFeature.STATS_PATTERN)),
        )
        AppType.BAZARR -> listOf(
            ServerHomeEntry(dev.appolon.homelab.arr.feature.bazarr.R.string.arr_bazarr_series_title, ArrRoute.Library(id, app)),
            ServerHomeEntry(dev.appolon.homelab.arr.feature.bazarr.R.string.arr_bazarr_movies_title, feature(BazarrLibraryFeature.ROUTE_OWNER, BazarrLibraryFeature.MOVIES_PATTERN)),
            ServerHomeEntry(UiR.string.arr_common_wanted_title, feature(BazarrWantedFeature.ROUTE_OWNER, BazarrWantedFeature.WANTED_PATTERN)),
            ServerHomeEntry(UiR.string.arr_common_history_title, feature(BazarrHistoryFeature.ROUTE_OWNER, BazarrHistoryFeature.HISTORY_PATTERN)),
            ServerHomeEntry(dev.appolon.homelab.arr.feature.bazarr.R.string.arr_bazarr_blacklist_title, feature(BazarrHistoryFeature.ROUTE_OWNER, BazarrHistoryFeature.BLACKLIST_PATTERN)),
        )
        else -> emptyList()
    }
}
