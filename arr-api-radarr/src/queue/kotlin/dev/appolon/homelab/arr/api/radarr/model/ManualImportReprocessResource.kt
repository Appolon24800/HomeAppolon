package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ManualImportReprocessResource. Dates remain unparsed strings. */
@Serializable
data class ManualImportReprocessResource(
    val id: Int = 0,
    val path: String? = null,
    val movieId: Int = 0,
    val movie: MovieResource = MovieResource(),
    val quality: QualityModel = QualityModel(),
    val languages: List<Language>? = null,
    val releaseGroup: String? = null,
    val downloadId: String? = null,
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
    val indexerFlags: Int = 0,
    val rejections: List<ImportRejectionResource>? = null,
)
