package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr IsoCountry wire resource. Dates remain unparsed strings. */
@Serializable
data class IsoCountry(
    val twoLetterCode: String? = null,
    val name: String? = null,
)
