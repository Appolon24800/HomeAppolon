package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** One quality (or nested quality group) inside a quality profile. */
@Serializable
data class QualityProfileQualityItemResource(
    val id: Int = 0,
    val name: String? = null,
    val quality: Quality? = null,
    val items: List<QualityProfileQualityItemResource>? = null,
    val allowed: Boolean = false,
)
