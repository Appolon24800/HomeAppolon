package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ManualImportReprocessResource wire contract. Dates remain unparsed strings. */
@Serializable
data class ManualImportReprocessResource(
    val id: Int = 0,
    val path: String? = null,
    val seriesId: Int = 0,
    val seasonNumber: Int? = null,
    val episodes: List<EpisodeResource>? = null,
    val episodeIds: List<Int>? = null,
    val quality: QualityModel = QualityModel(),
    val languages: List<Language>? = null,
    val releaseGroup: String? = null,
    val downloadId: String? = null,
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
    val indexerFlags: Int = 0,
    val releaseType: ReleaseType = ReleaseType.UNKNOWN,
    val rejections: List<ImportRejectionResource>? = null
)
