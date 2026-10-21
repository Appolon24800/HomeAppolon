package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr QualityDefinitionLimitsResource wire contract. */
@Serializable
data class QualityDefinitionLimitsResource(
    val min: Int = 0,
    val max: Int = 0
)
