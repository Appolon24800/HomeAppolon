package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr IndexerBulkResource.
 * Dates remain unparsed strings. */
@Serializable
data class IndexerBulkResource(
    val ids: List<Int>? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags? = null,
    val enableRss: Boolean? = null,
    val enableAutomaticSearch: Boolean? = null,
    val enableInteractiveSearch: Boolean? = null,
    val priority: Int? = null,
)
