package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr QualityProfileResource wire resource. Dates remain unparsed strings. */
@Serializable
data class QualityProfileResource(
    val id: Int = 0,
    val name: String? = null,
    val upgradeAllowed: Boolean = false,
    val cutoff: Int = 0,
    val items: List<QualityProfileQualityItemResource>? = null,
    val minFormatScore: Int = 0,
    val cutoffFormatScore: Int = 0,
    val formatItems: List<ProfileFormatItemResource>? = null,
)
