package dev.appolon.homelab.arr.feature.radarr.search.data

import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.core.Humanize

/**
 * Row model for the release list: everything the composable needs, with the
 * raw resource retained so the grab sheet can post the exact wire object.
 */
data class ReleaseRowUi(
    val release: RadarrReleaseResource,
    val title: String,
    val indexer: String,
    val qualityName: String,
    val qualityScore: Int,
    val customFormats: String,
    val protocolLabel: String,
    val ageDays: Int,
    val ageHoursRounded: Int,
    val sizeText: String,
    val seeders: Int?,
    val leechers: Int?,
    val rejections: List<String>,
    val languageNames: String,
) {
    val hasRejections: Boolean get() = rejections.isNotEmpty()
}

/** Maps a wire release onto its row, using [Humanize] for byte sizes. */
fun releaseRow(release: RadarrReleaseResource): ReleaseRowUi = ReleaseRowUi(
    release = release,
    title = release.title.orEmpty(),
    indexer = release.indexer.orEmpty(),
    qualityName = release.quality.quality?.name?.let(Humanize::qualityName).orEmpty(),
    qualityScore = release.customFormatScore,
    customFormats = release.customFormats.orEmpty()
        .mapNotNull { it.name }
        .joinToString(", "),
    protocolLabel = release.protocol.toString(),
    ageDays = release.age,
    ageHoursRounded = Math.round(release.ageHours).toInt(),
    sizeText = Humanize.bytes(release.size),
    seeders = release.seeders,
    leechers = release.leechers,
    rejections = release.rejections.orEmpty().filter { it.isNotBlank() },
    languageNames = release.languages.orEmpty().mapNotNull { it.name }.joinToString(", "),
)

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
 * Applies the grab sheet's overrides onto the release body: a chosen quality
 * replaces the parsed one (keeping its revision), a chosen language replaces
 * the parsed list. Null keeps what the indexer reported.
 */
fun applyGrabOverrides(
    release: RadarrReleaseResource,
    quality: Quality?,
    language: Language?,
): RadarrReleaseResource = release.copy(
    quality = if (quality == null) {
        release.quality
    } else {
        release.quality.copy(quality = quality)
    },
    languages = if (language == null) release.languages else listOf(language),
)
