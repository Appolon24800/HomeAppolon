package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr CustomFormatResource wire resource. Dates remain unparsed strings. */
@Serializable
data class CustomFormatResource(
    val id: Int = 0,
    val name: String? = null,
    val includeCustomFormatWhenRenaming: Boolean? = null,
    val specifications: List<CustomFormatSpecificationSchema>? = null,
)
