package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr QualityModel wire resource. Dates remain unparsed strings. */
@Serializable
data class QualityModel(
    val quality: Quality? = null,
    val revision: Revision? = null,
)
