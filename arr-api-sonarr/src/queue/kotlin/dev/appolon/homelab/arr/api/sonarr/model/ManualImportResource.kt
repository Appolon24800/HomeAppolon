package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ManualImportResource wire contract. Dates remain unparsed strings. */
@Serializable
data class ManualImportResource(
    val id: Int = 0,
    val path: String? = null,
    val relativePath: String? = null,
    val folderName: String? = null,
    val name: String? = null,
    val size: Long = 0L,
    val series: SeriesResource = SeriesResource(),
    val seasonNumber: Int? = null,
    val episodes: List<EpisodeResource>? = null,
    val episodeFileId: Int? = null,
    val releaseGroup: String? = null,
    val quality: QualityModel = QualityModel(),
    val languages: List<Language>? = null,
    val qualityWeight: Int = 0,
    val downloadId: String? = null,
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
    val indexerFlags: Int = 0,
    val releaseType: ReleaseType = ReleaseType.UNKNOWN,
    val rejections: List<ImportRejectionResource>? = null
)
