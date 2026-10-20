package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr MediaCover wire resource. Dates remain unparsed strings. */
@Serializable
data class MediaCover(
    val url: String? = null,
    val coverType: MediaCoverTypes = MediaCoverTypes.UNKNOWN,
    val extension: String? = null,
    val remoteUrl: String? = null,
)
