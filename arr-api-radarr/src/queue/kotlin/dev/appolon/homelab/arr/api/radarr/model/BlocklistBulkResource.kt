package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr BlocklistBulkResource. Dates remain unparsed strings. */
@Serializable
data class BlocklistBulkResource(
    val ids: List<Int>? = null,
)
