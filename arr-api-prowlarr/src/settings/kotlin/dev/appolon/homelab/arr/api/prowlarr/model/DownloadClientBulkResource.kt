package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Partial update applied to many download clients at once. Only [ids]
 * selects rows; the server skips every field left null. Tag merging follows
 * the spec's ApplyTags enum already modeled for indexers.
 */
@Serializable
data class DownloadClientBulkResource(
    val ids: List<Int>? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags = ApplyTags.REPLACE,
    val enable: Boolean? = null,
    val priority: Int? = null,
)
