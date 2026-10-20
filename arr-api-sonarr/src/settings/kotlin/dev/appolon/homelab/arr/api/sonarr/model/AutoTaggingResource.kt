package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr AutoTaggingResource wire contract. */
@Serializable
data class AutoTaggingResource(
    val id: Int = 0,
    val name: String? = null,
    val removeTagsAutomatically: Boolean = false,
    val tags: List<Int>? = null,
    val specifications: List<AutoTaggingSpecificationSchema>? = null
)
