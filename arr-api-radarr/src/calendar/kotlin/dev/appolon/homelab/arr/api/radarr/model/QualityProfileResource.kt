package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * A quality profile: the allowed quality tree, cutoff, and per-format scoring.
 * The schema endpoint returns a blank one of these for the editor form.
 */
@Serializable
data class QualityProfileResource(
    val id: Int = 0,
    val name: String? = null,
    val upgradeAllowed: Boolean = false,
    val cutoff: Int = 0,
    val items: List<QualityProfileQualityItemResource>? = null,
    val minFormatScore: Int = 0,
    val cutoffFormatScore: Int = 0,
    val minUpgradeFormatScore: Int = 0,
    val formatItems: List<ProfileFormatItemResource>? = null,
    val language: Language? = null,
)
