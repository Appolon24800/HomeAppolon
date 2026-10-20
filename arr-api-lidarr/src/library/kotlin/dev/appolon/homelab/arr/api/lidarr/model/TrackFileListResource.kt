package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr TrackFileListResource wire resource. Dates remain unparsed strings. */
@Serializable
data class TrackFileListResource(
    val trackFileIds: List<Int>? = null,
    val quality: QualityModel? = null,
    val sceneName: String? = null,
    val releaseGroup: String? = null,
)
