package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr ImportListBulkResource wire resource for bulk edit and delete. */
@Serializable
data class ImportListBulkResource(
    val ids: List<Int>? = null,
    val tags: Set<Int>? = null,
    val applyTags: ApplyTags? = null,
    val enableAutomaticAdd: Boolean? = null,
    val rootFolderPath: String? = null,
    val qualityProfileId: Int? = null,
)
