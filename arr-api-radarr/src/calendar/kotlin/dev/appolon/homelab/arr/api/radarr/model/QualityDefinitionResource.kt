package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * One row of the quality table edited on the Settings > Quality screen. Size
 * bounds are doubles in megabytes; the server allows null to mean unbounded.
 */
@Serializable
data class QualityDefinitionResource(
    val id: Int = 0,
    val quality: Quality? = null,
    val title: String? = null,
    val weight: Int = 0,
    val minSize: Double? = null,
    val maxSize: Double? = null,
    val preferredSize: Double? = null,
)
