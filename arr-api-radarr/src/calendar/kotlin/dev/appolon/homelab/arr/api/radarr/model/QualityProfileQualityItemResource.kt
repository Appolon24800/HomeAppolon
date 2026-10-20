package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * One selectable quality (or quality group) inside a quality profile. Groups
 * nest their members through `items`, which is how the schema endpoint models
 * Radarr's grouped quality rows.
 */
@Serializable
data class QualityProfileQualityItemResource(
    val id: Int = 0,
    val name: String? = null,
    val quality: Quality? = null,
    val items: List<QualityProfileQualityItemResource>? = null,
    val allowed: Boolean = false,
)
