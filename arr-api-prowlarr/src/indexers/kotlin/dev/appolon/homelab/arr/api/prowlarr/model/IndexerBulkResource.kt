package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a bulk edit merges [IndexerBulkResource.tags] and
 * [ApplicationBulkResource.tags] into the touched resources, from the spec's
 * ApplyTags enum.
 */
@Serializable
enum class ApplyTags {
    @SerialName("add") ADD,
    @SerialName("remove") REMOVE,
    @SerialName("replace") REPLACE,
}

/**
 * Partial update applied to many indexers at once. Only [ids] selects rows;
 * the server skips every field left null.
 */
@Serializable
data class IndexerBulkResource(
    val ids: List<Int>? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags = ApplyTags.REPLACE,
    val enable: Boolean? = null,
    val appProfileId: Int? = null,
    val priority: Int? = null,
    val minimumSeeders: Int? = null,
    val seedRatio: Double? = null,
    val seedTime: Int? = null,
    val packSeedTime: Int? = null,
    val preferMagnetUrl: Boolean? = null,
)
