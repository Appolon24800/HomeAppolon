package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr PingResource wire contract. Dates remain unparsed strings. */
@Serializable
data class PingResource(
    val status: String? = null
)
