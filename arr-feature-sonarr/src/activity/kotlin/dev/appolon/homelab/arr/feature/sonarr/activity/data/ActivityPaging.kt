package dev.appolon.homelab.arr.feature.sonarr.activity.data

import kotlinx.serialization.Serializable

/** Server page size for the history and blocklist endpoints; one load-more fetches one page. */
const val ACTIVITY_PAGE_SIZE = 50

/**
 * Hard ceiling on cached pages per list. History is append-only on the server
 * and can hold hundreds of thousands of rows; without a cap an idle scroll
 * session would grow the cache without bound. Fifty pages of fifty rows is far
 * beyond what the feed is read for, and pull-to-refresh recenters on page one.
 */
const val ACTIVITY_MAX_PAGES = 50

/**
 * Paging cursor for one activity list: the server total (drives end-of-list)
 * plus how many pages the cache already holds.
 */
@Serializable
data class ActivityListMeta(
    val totalRecords: Int = 0,
    val pagesLoaded: Int = 0,
) {
    /** True when every relevant server page is already in the cache. */
    fun endReached(cachedCount: Int): Boolean =
        cachedCount >= totalRecords || pagesLoaded >= ACTIVITY_MAX_PAGES

    /** The next page to request, or null when the cursor is exhausted. */
    fun nextPage(): Int? = if (pagesLoaded >= ACTIVITY_MAX_PAGES) null else pagesLoaded + 1
}

/** Result of appending one server page to the cache. */
data class ActivityPageLoad(
    val appended: Int,
    val meta: ActivityListMeta,
    val endReached: Boolean,
)
