package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr Ratings wire resource. Dates remain unparsed strings. */
@Serializable
data class Ratings(
    val votes: Int = 0,
    val value: Double = 0.0,
)
