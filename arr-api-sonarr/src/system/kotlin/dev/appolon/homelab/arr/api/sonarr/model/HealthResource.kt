package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr HealthResource wire contract. */
@Serializable
data class HealthResource(
    val id: Int = 0,
    val source: String? = null,
    val type: HealthCheckResult? = null,
    val message: String? = null,
    val wikiUrl: String? = null
)
