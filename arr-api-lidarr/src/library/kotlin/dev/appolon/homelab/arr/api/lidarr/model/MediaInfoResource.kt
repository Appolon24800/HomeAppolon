package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr MediaInfoResource wire resource. Dates remain unparsed strings. */
@Serializable
data class MediaInfoResource(
    val id: Int = 0,
    val audioChannels: Double = 0.0,
    val audioBitRate: String? = null,
    val audioCodec: String? = null,
    val audioBits: String? = null,
    val audioSampleRate: String? = null,
)
