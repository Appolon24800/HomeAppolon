package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr TrackFileResource wire resource. Dates remain unparsed strings. */
@Serializable
data class TrackFileResource(
    val id: Int = 0,
    val artistId: Int = 0,
    val albumId: Int = 0,
    val path: String? = null,
    val size: Long = 0L,
    val dateAdded: String = "",
    val sceneName: String? = null,
    val releaseGroup: String? = null,
    val quality: QualityModel? = null,
    val qualityWeight: Int = 0,
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
    val indexerFlags: Int? = null,
    val mediaInfo: MediaInfoResource? = null,
    val qualityCutoffNotMet: Boolean = false,
    val audioTags: ParsedTrackInfo? = null,
)
