package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ImportListExclusionBulkResource wire contract for bulk delete. */
@Serializable
data class ImportListExclusionBulkResource(
    val ids: List<Int>? = null
)
