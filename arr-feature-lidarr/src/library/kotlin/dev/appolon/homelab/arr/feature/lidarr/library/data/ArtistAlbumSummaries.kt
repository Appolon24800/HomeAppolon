package dev.appolon.homelab.arr.feature.lidarr.library.data

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.core.ArrTime
import java.time.Instant

/**
 * Per-artist album rollups the library renders and filters on. Lidarr's artist
 * resource carries only a flat `albumCount`, so release-status and missing
 * counts derive client-side from the album list, the same data the web UI's
 * album studio page works from.
 */
data class ArtistAlbumSummary(
    val artistId: Int,
    val albumCount: Int = 0,
    /** Albums whose release date has passed. */
    val released: Int = 0,
    /** Albums announced with a future release date. */
    val upcoming: Int = 0,
    /** Albums without a parseable release date. */
    val tba: Int = 0,
    /** Monitored albums with no track files, the "missing" notion the brief names. */
    val missing: Int = 0,
) {
    val hasMissing: Boolean get() = missing > 0
}

/** Release bucket of one album derived from its release date. */
internal enum class AlbumReleaseStatus { RELEASED, UPCOMING, TBA }

internal fun releaseStatusOf(album: AlbumResource, now: Instant): AlbumReleaseStatus {
    val date = ArrTime.parse(album.releaseDate) ?: return AlbumReleaseStatus.TBA
    return if (date.isAfter(now)) AlbumReleaseStatus.UPCOMING else AlbumReleaseStatus.RELEASED
}

/** True when an album counts as missing: monitored but no files on disk. */
internal fun isMissingAlbum(album: AlbumResource): Boolean =
    album.monitored && (album.statistics?.trackFileCount ?: 0) == 0

/**
 * Folds the full album list into per-artist summaries. Pure so tests pin the
 * bucket rules; albums whose artist is absent from the library are skipped by
 * the caller before folding.
 */
fun artistAlbumSummaries(
    albums: List<AlbumResource>,
    now: Instant = Instant.now(),
): Map<Int, ArtistAlbumSummary> {
    val summaries = HashMap<Int, MutableRollup>()
    albums.forEach { album ->
        val rollup = summaries.getOrPut(album.artistId) { MutableRollup(album.artistId) }
        rollup.albumCount++
        when (releaseStatusOf(album, now)) {
            AlbumReleaseStatus.RELEASED -> rollup.released++
            AlbumReleaseStatus.UPCOMING -> rollup.upcoming++
            AlbumReleaseStatus.TBA -> rollup.tba++
        }
        if (isMissingAlbum(album)) rollup.missing++
    }
    return summaries.mapValues { (_, rollup) -> rollup.toSummary() }
}

private class MutableRollup(val artistId: Int) {
    var albumCount = 0
    var released = 0
    var upcoming = 0
    var tba = 0
    var missing = 0

    fun toSummary() = ArtistAlbumSummary(
        artistId = artistId,
        albumCount = albumCount,
        released = released,
        upcoming = upcoming,
        tba = tba,
        missing = missing,
    )
}
