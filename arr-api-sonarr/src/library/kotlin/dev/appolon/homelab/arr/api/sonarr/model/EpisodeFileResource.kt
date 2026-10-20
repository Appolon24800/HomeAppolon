package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class EpisodeFileResource(
    val id: Int = 0,
    val seriesId: Int = 0,
    val seasonNumber: Int = 0,
    val relativePath: String? = null,
    val path: String? = null,
    val size: Long = 0L,
    val dateAdded: String = "1970-01-01T00:00:00Z",
    val sceneName: String? = null,
    val releaseGroup: String? = null,
    val languages: List<Language>? = null,
    val quality: QualityModel = QualityModel(),
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
    val indexerFlags: Int? = null,
    val releaseType: ReleaseType = ReleaseType.UNKNOWN,
    val mediaInfo: MediaInfoResource = MediaInfoResource(),
    val qualityCutoffNotMet: Boolean = false
)
