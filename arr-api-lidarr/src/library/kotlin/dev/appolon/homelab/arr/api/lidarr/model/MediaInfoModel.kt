package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr MediaInfoModel wire resource. Dates remain unparsed strings. */
@Serializable
data class MediaInfoModel(
    val audioFormat: String? = null,
    val audioBitrate: Int = 0,
    val audioChannels: Int = 0,
    val audioBits: Int = 0,
    val audioSampleRate: Int = 0,
)
