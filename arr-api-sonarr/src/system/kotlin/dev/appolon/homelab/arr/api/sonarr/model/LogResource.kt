package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr LogResource wire contract. Dates remain unparsed strings. */
@Serializable
data class LogResource(
    val id: Int = 0,
    val time: String? = null,
    val exception: String? = null,
    val exceptionType: String? = null,
    val level: String? = null,
    val logger: String? = null,
    val message: String? = null,
    val method: String? = null
)
