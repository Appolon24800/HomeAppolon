package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr QualityProfileResource wire contract. */
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
    val formatItems: List<ProfileFormatItemResource>? = null
)
