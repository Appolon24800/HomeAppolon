package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr MediaCover wire resource. Dates remain unparsed strings. */
@Serializable
data class MediaCover(
    val url: String? = null,
    val coverType: MediaCoverTypes = MediaCoverTypes.UNKNOWN,
    val extension: String? = null,
    val remoteUrl: String? = null,
)
