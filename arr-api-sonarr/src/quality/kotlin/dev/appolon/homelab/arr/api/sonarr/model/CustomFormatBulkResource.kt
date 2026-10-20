package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr CustomFormatBulkResource wire contract for bulk update and delete. */
@Serializable
data class CustomFormatBulkResource(
    val ids: List<Int>? = null,
    val includeCustomFormatWhenRenaming: Boolean? = null
)
