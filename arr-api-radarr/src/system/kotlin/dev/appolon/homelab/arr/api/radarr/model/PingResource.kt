package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr PingResource. Dates remain unparsed strings. */
@Serializable
data class PingResource(
    val status: String? = null,
)
