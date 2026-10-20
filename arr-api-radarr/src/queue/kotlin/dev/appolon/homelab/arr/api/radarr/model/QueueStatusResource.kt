package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr QueueStatusResource. Dates remain unparsed strings. */
@Serializable
data class QueueStatusResource(
    val id: Int = 0,
    val totalCount: Int = 0,
    val count: Int = 0,
    val unknownCount: Int = 0,
    val errors: Boolean = false,
    val warnings: Boolean = false,
    val unknownErrors: Boolean = false,
    val unknownWarnings: Boolean = false,
)
