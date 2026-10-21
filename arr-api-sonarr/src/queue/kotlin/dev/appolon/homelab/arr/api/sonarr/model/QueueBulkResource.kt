package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr QueueBulkResource wire contract. Dates remain unparsed strings. */
@Serializable
data class QueueBulkResource(
    val ids: List<Int>? = null
)
