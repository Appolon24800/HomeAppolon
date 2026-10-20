package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ImportListExclusionBulkResource.
 * Dates remain unparsed strings. */
@Serializable
data class ImportListExclusionBulkResource(
    val ids: Set<Int>? = null,
)
