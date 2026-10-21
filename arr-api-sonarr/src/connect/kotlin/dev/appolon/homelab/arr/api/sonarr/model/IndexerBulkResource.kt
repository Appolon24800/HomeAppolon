package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Partial update applied to every indexer in [ids]; null fields are ignored. */
@Serializable
data class IndexerBulkResource(
    val ids: List<Int>? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags = ApplyTags.ADD,
    val enableRss: Boolean? = null,
    val enableAutomaticSearch: Boolean? = null,
    val enableInteractiveSearch: Boolean? = null,
    val priority: Int? = null,
    val seasonSearchMaximumSingleEpisodeAge: Int? = null
)
