package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr QualityProfileQualityItemResource wire contract. Group items nest members. */
@Serializable
data class QualityProfileQualityItemResource(
    val id: Int = 0,
    val name: String? = null,
    val quality: Quality = Quality(),
    val items: List<QualityProfileQualityItemResource>? = null,
    val allowed: Boolean = false,
    val minSize: Double? = null,
    val maxSize: Double? = null,
    val preferredSize: Double? = null
)
