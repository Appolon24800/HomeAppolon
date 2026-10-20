package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr HealthResource wire resource for one health check row. */
@Serializable
data class HealthResource(
    val id: Int = 0,
    val source: String? = null,
    val type: HealthCheckResult? = null,
    val message: String? = null,
    val wikiUrl: String? = null,
)
