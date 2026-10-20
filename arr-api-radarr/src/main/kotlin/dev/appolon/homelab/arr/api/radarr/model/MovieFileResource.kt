package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * A physical video file owned by a movie. Sizes are int64 on the wire and can
 * exceed an Int, so they stay Long end to end.
 */
@Serializable
data class MovieFileResource(
    val id: Int = 0,
    val movieId: Int = 0,
    val relativePath: String? = null,
    val path: String? = null,
    val size: Long = 0L,
    val dateAdded: String? = null,
    val sceneName: String? = null,
    val releaseGroup: String? = null,
    val edition: String? = null,
    val languages: List<Language>? = null,
    val quality: QualityModel? = null,
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int? = null,
    val indexerFlags: Int? = null,
    val mediaInfo: MediaInfoResource? = null,
    val originalFilePath: String? = null,
    val qualityCutoffNotMet: Boolean = false,
)

/**
 * Batch edit descriptor for the moviefile bulk delete and (legacy) editor
 * endpoints: the listed files all receive the same patch.
 */
@Serializable
data class MovieFileListResource(
    val movieFileIds: List<Int>? = null,
    val languages: List<Language>? = null,
    val quality: QualityModel? = null,
    val edition: String? = null,
    val releaseGroup: String? = null,
    val sceneName: String? = null,
    val indexerFlags: Int? = null,
)
