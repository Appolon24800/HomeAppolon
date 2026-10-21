package dev.appolon.homelab.arr.feature.sonarr.search.data

import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
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
    val seasonPack: Boolean,
    val episodeLabel: String?,
    val ageDays: Int,
    val ageHoursRounded: Int,
    val sizeText: String,
    val seeders: Int?,
    val leechers: Int?,
    val rejections: List<String>,
    val languageNames: String,
    val approved: Boolean,
) {
    val hasRejections: Boolean get() = rejections.isNotEmpty()
}

/** Maps a wire release onto its row, using [Humanize] for byte sizes. */
fun releaseRow(release: ReleaseResource): ReleaseRowUi = ReleaseRowUi(
    release = release,
    title = release.title.orEmpty(),
    indexer = release.indexer.orEmpty(),
    qualityName = Humanize.qualityName(release.quality.quality.name.orEmpty()),
    qualityScore = release.customFormatScore,
    customFormats = release.customFormats.orEmpty()
        .mapNotNull { it.name }
        .joinToString(", "),
    protocolLabel = release.protocol.toString(),
    seasonPack = release.fullSeason,
    episodeLabel = episodeLabelOf(release),
    ageDays = release.age,
    ageHoursRounded = Math.round(release.ageHours).toInt(),
    sizeText = Humanize.bytes(release.size),
    seeders = release.seeders,
    leechers = release.leechers,
    rejections = release.rejections.orEmpty().filter { it.isNotBlank() },
    languageNames = release.languages.orEmpty().mapNotNull { it.name }.joinToString(", "),
    approved = release.approved,
)

/**
 * "S02E05" for a single-episode release, "S02E05-E07" spanning several, and
 * null for season packs and daily episodes without numbers. Mapped numbers
 * win when the indexer's scene numbering differs from Sonarr's.
 */
fun episodeLabelOf(release: ReleaseResource): String? {
    if (release.seasonNumber <= 0) return null
    val numbers = release.mappedEpisodeNumbers ?: release.episodeNumbers
    val episodeNumbers = numbers.orEmpty().filter { it > 0 }
    if (episodeNumbers.isEmpty()) return null
    val first = episodeNumbers.min()
    val last = episodeNumbers.max()
    val head = episodeLabel(release.seasonNumber, first)
    return if (first == last) head else head + "-E" + last.toString().padStart(2, '0')
}

/** "S02E05"; local twin of the queue group's label so the groups stay independent. */
private fun episodeLabel(seasonNumber: Int, episodeNumber: Int): String =
    "S" + seasonNumber.toString().padStart(2, '0') + "E" + episodeNumber.toString().padStart(2, '0')

/**
 * Applies the season toggle of a series or season search: season packs keep
 * only `fullSeason` rows, episodes drop them. Episode scope passes ALL
 * through unchanged; the screen hides the toggle there.
 */
fun filterReleaseRows(
    rows: List<ReleaseRowUi>,
    filter: ReleaseGroupFilter,
): List<ReleaseRowUi> = when (filter) {
    ReleaseGroupFilter.ALL -> rows
    ReleaseGroupFilter.SEASON_PACKS -> rows.filter { it.seasonPack }
    ReleaseGroupFilter.EPISODES -> rows.filter { !it.seasonPack }
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
 * Applies the grab sheet's overrides onto the release body: a chosen quality
 * replaces the parsed one (keeping its revision), a chosen language replaces
 * the parsed list. Null keeps what the indexer reported.
 */
fun applyGrabOverrides(
    release: ReleaseResource,
    quality: Quality?,
    language: Language?,
): ReleaseResource = release.copy(
    quality = if (quality == null) {
        release.quality
    } else {
        release.quality.copy(quality = quality)
    },
    languages = if (language == null) release.languages else listOf(language),
)
