package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr AutoTaggingResource wire resource for one auto tagging rule set. */
@Serializable
data class AutoTaggingResource(
    val id: Int = 0,
    val name: String? = null,
    val removeTagsAutomatically: Boolean = false,
    val tags: Set<Int>? = null,
    val specifications: List<AutoTaggingSpecificationSchema>? = null,
)
