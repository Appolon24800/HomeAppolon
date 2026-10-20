package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr MetadataConfigResource. Dates remain unparsed strings. */
@Serializable
data class MetadataConfigResource(
    val id: Int = 0,
    val certificationCountry: TMDbCountryCode? = null,
)
