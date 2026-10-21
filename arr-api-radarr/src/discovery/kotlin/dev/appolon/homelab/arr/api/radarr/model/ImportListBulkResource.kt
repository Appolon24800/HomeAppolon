package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ImportListBulkResource.
 * Dates remain unparsed strings. */
@Serializable
data class ImportListBulkResource(
    val ids: List<Int>? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags? = null,
    val enabled: Boolean? = null,
    val enableAuto: Boolean? = null,
    val rootFolderPath: String? = null,
    val qualityProfileId: Int? = null,
    val minimumAvailability: MovieStatusType? = null,
)
