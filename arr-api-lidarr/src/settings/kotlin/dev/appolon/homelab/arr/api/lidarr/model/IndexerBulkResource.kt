package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr IndexerBulkResource wire resource for bulk edit and delete. */
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
