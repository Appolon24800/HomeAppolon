package dev.appolon.homelab.arr.feature.lidarr.search.data

import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import kotlinx.serialization.Serializable

/**
 * Which slice of the library the interactive search covers. Lidarr's release
 * endpoint takes either an artistId or an albumId, so the scope decides both
 * the query and the cache row: artist scope returns every album release and
 * discography pack the indexers hold for the artist, album scope narrows to
 * one album's release packs.
 */
sealed interface ReleaseScope {
    /** Every release the indexers hold for the artist, all albums. */
    data class Artist(val artistId: Int) : ReleaseScope

    /** One album's release packs; the artist-scope row stays separate. */
    data class Album(val albumId: Int) : ReleaseScope

    /** Cache row key; one stable key per scope so pages survive rotation. */
    fun cacheKey(): String = when (this) {
        is Artist -> "artist-$artistId"
        is Album -> "album-$albumId"
    }
}

/**
 * One scope's cached search result page. Stored as a single entity row so the
 * per-scope fetchedAt stamp lives with the data; the store's type-wide
 * `lastFetchedAt` cannot express per-scope freshness.
 */
@Serializable
data class LidarrReleasePage(
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
 * The artist-scope toggle from the feature matrix: single album packs
 * versus whole-discography releases. Album scope always shows the one
 * album's packs, so the screen hides the toggle there.
 */
enum class ReleaseGroupFilter {
    ALL,
    ALBUM_PACKS,
    DISCOGRAPHY,
}
