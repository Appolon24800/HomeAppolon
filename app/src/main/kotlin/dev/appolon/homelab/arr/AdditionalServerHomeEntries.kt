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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.ui.graphics.vector.ImageVector

internal data class ServerHomeEntry(val title: Int, val route: ArrRoute, val icon: ImageVector)

/** Entry menus for apps whose library does not link their other feature groups. */
internal fun additionalServerHomeEntries(app: AppType, id: ServerId): List<ServerHomeEntry> {
    fun feature(owner: String, pattern: String) = ArrRoute.Feature(
        owner,
        checkNotNull(fillPattern(pattern, mapOf("serverId" to id.value))),
    )
    return when (app) {
        AppType.PROWLARR -> listOf(
            ServerHomeEntry(UiR.string.arr_common_indexers_title, ArrRoute.Library(id, app), Icons.Outlined.Dns),
            ServerHomeEntry(AppR.string.arr_home_search, checkNotNull(ProwlarrSearchFeature.route(id)), Icons.Outlined.Search),
            ServerHomeEntry(AppR.string.arr_home_search_history, checkNotNull(ProwlarrSearchFeature.route(id, SearchPage.HISTORY)), Icons.AutoMirrored.Outlined.ManageSearch),
            ServerHomeEntry(AppR.string.arr_home_search_stats, checkNotNull(ProwlarrSearchFeature.route(id, SearchPage.STATS)), Icons.Outlined.QueryStats),
            ServerHomeEntry(UiR.string.arr_common_history_title, feature(ProwlarrHistoryFeature.ROUTE_OWNER, ProwlarrHistoryFeature.HISTORY_PATTERN), Icons.Outlined.History),
            ServerHomeEntry(dev.appolon.homelab.arr.feature.prowlarr.R.string.arr_prowlarr_stats_title, feature(ProwlarrStatsFeature.ROUTE_OWNER, ProwlarrStatsFeature.STATS_PATTERN), Icons.Outlined.BarChart),
        )
        AppType.BAZARR -> listOf(
            ServerHomeEntry(dev.appolon.homelab.arr.feature.bazarr.R.string.arr_bazarr_series_title, ArrRoute.Library(id, app), Icons.Outlined.Tv),
            ServerHomeEntry(dev.appolon.homelab.arr.feature.bazarr.R.string.arr_bazarr_movies_title, feature(BazarrLibraryFeature.ROUTE_OWNER, BazarrLibraryFeature.MOVIES_PATTERN), Icons.Outlined.Movie),
            ServerHomeEntry(UiR.string.arr_common_wanted_title, feature(BazarrWantedFeature.ROUTE_OWNER, BazarrWantedFeature.WANTED_PATTERN), Icons.Outlined.Subtitles),
            ServerHomeEntry(UiR.string.arr_common_history_title, feature(BazarrHistoryFeature.ROUTE_OWNER, BazarrHistoryFeature.HISTORY_PATTERN), Icons.Outlined.History),
            ServerHomeEntry(dev.appolon.homelab.arr.feature.bazarr.R.string.arr_bazarr_blacklist_title, feature(BazarrHistoryFeature.ROUTE_OWNER, BazarrHistoryFeature.BLACKLIST_PATTERN), Icons.Outlined.Block),
        )
        else -> emptyList()
    }
}
