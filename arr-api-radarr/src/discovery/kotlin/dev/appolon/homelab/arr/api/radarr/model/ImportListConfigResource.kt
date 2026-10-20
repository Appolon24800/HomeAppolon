package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ImportListConfigResource.
 * Dates remain unparsed strings. */
@Serializable
data class ImportListConfigResource(
    val id: Int = 0,
    val listSyncLevel: String? = null,
)
