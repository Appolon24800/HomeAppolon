package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr QueueBulkResource. Dates remain unparsed strings. */
@Serializable
data class QueueBulkResource(
    val ids: List<Int>? = null,
)
