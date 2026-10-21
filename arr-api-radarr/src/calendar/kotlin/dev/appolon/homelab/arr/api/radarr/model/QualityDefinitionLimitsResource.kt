package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Server-enforced bounds for quality definition weights, in whole numbers. */
@Serializable
data class QualityDefinitionLimitsResource(
    val min: Int = 0,
    val max: Int = 0,
)
