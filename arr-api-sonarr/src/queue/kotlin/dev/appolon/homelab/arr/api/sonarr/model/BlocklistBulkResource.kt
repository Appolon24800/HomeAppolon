package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr BlocklistBulkResource wire contract. Dates remain unparsed strings. */
@Serializable
data class BlocklistBulkResource(
    val ids: List<Int>? = null
)
