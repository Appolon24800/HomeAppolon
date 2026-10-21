package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr HealthResource. Dates remain unparsed strings. */
@Serializable
data class HealthResource(
    val id: Int = 0,
    val source: String? = null,
    val type: HealthCheckResult? = null,
    val message: String? = null,
    val wikiUrl: String? = null,
)
