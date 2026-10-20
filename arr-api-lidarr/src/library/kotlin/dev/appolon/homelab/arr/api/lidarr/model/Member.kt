package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr Member wire resource. Dates remain unparsed strings. */
@Serializable
data class Member(
    val name: String? = null,
    val instrument: String? = null,
    val images: List<MediaCover>? = null,
)
