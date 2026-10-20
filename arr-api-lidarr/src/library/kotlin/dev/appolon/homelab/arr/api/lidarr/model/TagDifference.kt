package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr TagDifference wire resource. Dates remain unparsed strings. */
@Serializable
data class TagDifference(
    val field: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
)
