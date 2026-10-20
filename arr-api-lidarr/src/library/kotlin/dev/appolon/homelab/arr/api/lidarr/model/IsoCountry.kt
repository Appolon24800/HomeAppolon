package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr IsoCountry wire resource. Dates remain unparsed strings. */
@Serializable
data class IsoCountry(
    val twoLetterCode: String? = null,
    val name: String? = null,
)
