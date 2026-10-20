package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ImportListBulkResource wire contract for editor bulk update and bulk delete. */
@Serializable
data class ImportListBulkResource(
    val ids: List<Int>? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags = ApplyTags.ADD,
    val enableAutomaticAdd: Boolean? = null,
    val rootFolderPath: String? = null,
    val qualityProfileId: Int? = null,
    val tagExisting: TagExistingType = TagExistingType.NONE
)
