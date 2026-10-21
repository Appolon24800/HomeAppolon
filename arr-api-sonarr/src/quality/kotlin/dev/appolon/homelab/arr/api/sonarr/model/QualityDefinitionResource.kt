package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr QualityDefinitionResource wire contract. Sizes are megabytes. */
@Serializable
data class QualityDefinitionResource(
    val id: Int = 0,
    val quality: Quality = Quality(),
    val title: String? = null,
    val weight: Int = 0,
    val minSize: Double? = null,
    val maxSize: Double? = null,
    val preferredSize: Double? = null
)
