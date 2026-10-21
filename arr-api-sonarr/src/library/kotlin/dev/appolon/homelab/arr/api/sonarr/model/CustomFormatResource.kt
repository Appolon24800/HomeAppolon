package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomFormatResource(
    val id: Int = 0,
    val name: String? = null,
    val includeCustomFormatWhenRenaming: Boolean? = null,
    val specifications: List<CustomFormatSpecificationSchema>? = null
)
