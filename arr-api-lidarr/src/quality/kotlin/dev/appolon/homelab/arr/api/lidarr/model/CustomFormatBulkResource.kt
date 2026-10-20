package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Bulk edit payload for custom formats. */
@Serializable
data class CustomFormatBulkResource(
    val ids: List<Int>? = null,
    val includeCustomFormatWhenRenaming: Boolean? = null,
)
