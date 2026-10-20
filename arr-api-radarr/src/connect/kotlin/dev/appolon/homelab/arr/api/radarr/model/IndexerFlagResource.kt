package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr IndexerFlagResource.
 * Dates remain unparsed strings. */
@Serializable
data class IndexerFlagResource(
    val id: Int = 0,
    val name: String? = null,
    val nameLower: String? = null,
)
