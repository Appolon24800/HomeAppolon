package dev.appolon.homelab.arr.feature.sonarr.search.data

import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import kotlinx.serialization.Serializable

/**
 * Which slice of a series the interactive search covers. Sonarr's release
 * endpoint takes seriesId plus either a seasonNumber or an episodeId, so the
 * scope decides both the query and the cache row.
 */
sealed interface ReleaseScope {
    val seriesId: Int

    /** Every release the indexers hold for the series, all seasons. */
    data class Series(override val seriesId: Int) : ReleaseScope

    /** One season: season packs plus its individual episode releases. */
    data class Season(override val seriesId: Int, val seasonNumber: Int) : ReleaseScope

    /** One episode, matching Sonarr's per-episode interactive search. */
    data class Episode(override val seriesId: Int, val episodeId: Int) : ReleaseScope

    /** Cache row key; one stable key per scope so pages survive rotation. */
    fun cacheKey(): String = when (this) {
        is Series -> "series-$seriesId"
        is Season -> "series-$seriesId-season-$seasonNumber"
        is Episode -> "series-$seriesId-episode-$episodeId"
    }
}

/**
 * One scope's cached search result page. Stored as a single entity row so the
 * per-scope fetchedAt stamp lives with the data; the store's type-wide
 * `lastFetchedAt` cannot express per-scope freshness.
 */
@Serializable
data class SonarrReleasePage(
    val scopeKey: String,
    val releases: List<ReleaseResource> = emptyList(),
    val fetchedAtMillis: Long = 0,
)

/** Sort keys the release list offers, per the feature matrix. */
enum class ReleaseSort {
    /** Custom format score, highest first; ties break by release weight. */
    QUALITY_SCORE,

    /** Seeders, most first; usenet rows without seeders sort last. */
    SEEDERS,

    /** Age, youngest first, matching the web UI's age column default. */
    AGE,

    /** Size, largest first. */
    SIZE,
}

/**
 * The season-scope toggle from the feature matrix: season packs versus
 * individual episodes. Sonarr returns both kinds for a season (or a whole
 * series); episode scope always shows everything the indexers matched.
 */
enum class ReleaseGroupFilter {
    ALL,
    SEASON_PACKS,
    EPISODES,
}
