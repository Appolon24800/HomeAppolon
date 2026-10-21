package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ParsedEpisodeInfoResource wire contract. Dates remain unparsed strings. */
@Serializable
data class ParsedEpisodeInfoResource(
    val releaseTitle: String? = null,
    val seriesTitle: String? = null,
    val seriesTitleInfo: SeriesTitleInfo = SeriesTitleInfo(),
    val quality: QualityModel = QualityModel(),
    val seasonNumbers: List<Int>? = null,
    val seasonNumber: Int = 0,
    val episodeNumbers: List<Int>? = null,
    val absoluteEpisodeNumbers: List<Int>? = null,
    val specialAbsoluteEpisodeNumbers: List<Double>? = null,
    val airDate: String? = null,
    val languages: List<Language>? = null,
    val fullSeason: Boolean = false,
    val isPartialSeason: Boolean = false,
    val isMultiSeason: Boolean = false,
    val isSeasonExtra: Boolean = false,
    val isSplitEpisode: Boolean = false,
    val isMiniSeries: Boolean = false,
    val special: Boolean = false,
    val releaseGroup: String? = null,
    val releaseHash: String? = null,
    val seasonPart: Int = 0,
    val releaseTokens: String? = null,
    val dailyPart: Int? = null,
    val isDaily: Boolean = false,
    val isAbsoluteNumbering: Boolean = false,
    val isPossibleSpecialEpisode: Boolean = false,
    val isPossibleSceneSeasonSpecial: Boolean = false,
    val releaseType: ReleaseType = ReleaseType.UNKNOWN
)
