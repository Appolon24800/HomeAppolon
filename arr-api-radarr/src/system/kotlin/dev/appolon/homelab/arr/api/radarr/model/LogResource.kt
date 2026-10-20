package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr LogResource. Dates remain unparsed strings. */
@Serializable
data class LogResource(
    val id: Int = 0,
    val time: String = "",
    val exception: String? = null,
    val exceptionType: String? = null,
    val level: String? = null,
    val logger: String? = null,
    val message: String? = null,
    val method: String? = null,
)
