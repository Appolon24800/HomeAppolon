package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ApiInfoResource. Dates remain unparsed strings. */
@Serializable
data class ApiInfoResource(
    val current: String? = null,
    val deprecated: List<String>? = null,
)
