package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * Bulk edit and bulk delete body for custom formats. Both operations accept the
 * same shape; delete ignores the rename flag.
 */
@Serializable
data class CustomFormatBulkResource(
    val ids: Set<Int>? = null,
    val includeCustomFormatWhenRenaming: Boolean? = null,
)
