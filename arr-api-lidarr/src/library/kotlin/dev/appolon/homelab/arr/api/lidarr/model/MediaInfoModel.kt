package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr MediaInfoModel wire resource. Dates remain unparsed strings. */
@Serializable
data class MediaInfoModel(
    val audioFormat: String? = null,
    val audioBitrate: Int = 0,
    val audioChannels: Int = 0,
    val audioBits: Int = 0,
    val audioSampleRate: Int = 0,
)
