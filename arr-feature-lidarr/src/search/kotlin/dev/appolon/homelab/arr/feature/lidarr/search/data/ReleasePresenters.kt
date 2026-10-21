package dev.appolon.homelab.arr.feature.lidarr.search.data

import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityModel
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.core.Humanize

/**
 * Row model for the release list: everything the composable needs, with the
 * raw resource retained so the grab sheet can post the exact wire object.
 */
data class ReleaseRowUi(
    val release: ReleaseResource,
    val title: String,
    val indexer: String,
    val qualityName: String,
    val qualityScore: Int,
    val customFormats: String,
    val protocolLabel: String,
    val discography: Boolean,
    val albumTitle: String?,
    val artistName: String?,
    val releaseGroupLabel: String?,
    val ageDays: Int,
    val ageHoursRounded: Int,
    val sizeText: String,
    val seeders: Int?,
    val leechers: Int?,
    val rejections: List<String>,
    val approved: Boolean,
) {
    val hasRejections: Boolean get() = rejections.isNotEmpty()
}

/** Maps a wire release onto its row, using [Humanize] for byte sizes. */
fun releaseRow(release: ReleaseResource): ReleaseRowUi = ReleaseRowUi(
    release = release,
    title = release.title.orEmpty(),
    indexer = release.indexer.orEmpty(),
    qualityName = Humanize.qualityName(release.quality?.quality?.name.orEmpty()),
    qualityScore = release.customFormatScore,
    customFormats = release.customFormats.orEmpty()
        .mapNotNull { it.name }
        .joinToString(", "),
    protocolLabel = release.protocol.toString(),
    discography = release.discography,
    albumTitle = release.albumTitle?.takeIf { it.isNotEmpty() },
    artistName = release.artistName?.takeIf { it.isNotEmpty() },
    releaseGroupLabel = release.releaseGroup?.takeIf { it.isNotEmpty() },
    ageDays = release.age,
    ageHoursRounded = Math.round(release.ageHours).toInt(),
    sizeText = Humanize.bytes(release.size),
    seeders = release.seeders,
    leechers = release.leechers,
    rejections = release.rejections.orEmpty().filter { it.isNotBlank() },
    approved = release.approved,
)

/**
 * Applies the artist-scope toggle: discography rows keep only
 * `discography` releases, album packs drop them. Album scope passes ALL
 * through unchanged; the screen hides the toggle there.
 */
fun filterReleaseRows(
    rows: List<ReleaseRowUi>,
    filter: ReleaseGroupFilter,
): List<ReleaseRowUi> = when (filter) {
    ReleaseGroupFilter.ALL -> rows
    ReleaseGroupFilter.ALBUM_PACKS -> rows.filter { !it.discography }
    ReleaseGroupFilter.DISCOGRAPHY -> rows.filter { it.discography }
}

/**
 * Sorts release rows for the list. Every key sorts descending except age,
 * which puts the youngest release first; rows missing the key (usenet has no
 * seeders) sink to the bottom instead of jumping around on null.
 */
fun sortReleaseRows(rows: List<ReleaseRowUi>, sort: ReleaseSort): List<ReleaseRowUi> =
    when (sort) {
        ReleaseSort.QUALITY_SCORE -> rows.sortedWith(
            compareByDescending<ReleaseRowUi> { it.qualityScore }
                .thenByDescending { it.release.releaseWeight },
        )

        ReleaseSort.SEEDERS -> rows.sortedWith(
            compareByDescending<ReleaseRowUi> { it.seeders != null }
                .thenByDescending { it.seeders ?: 0 },
        )

        ReleaseSort.AGE -> rows.sortedWith(
            compareBy<ReleaseRowUi> { it.release.ageHours }
                .thenBy { it.release.ageMinutes },
        )

        ReleaseSort.SIZE -> rows.sortedByDescending { it.release.size }
    }

/**
 * Applies the grab sheet's override onto the release body: a chosen quality
 * replaces the parsed one, keeping the parsed revision when there was one.
 * Null keeps what the indexer reported.
 */
fun applyGrabOverrides(
    release: ReleaseResource,
    quality: Quality?,
): ReleaseResource = if (quality == null) {
    release
} else {
    release.copy(
        quality = release.quality?.copy(quality = quality)
            ?: QualityModel(quality = quality),
    )
}
