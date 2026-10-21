package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * One server health finding from `GET /api/v1/health`; `wikiUrl` links to
 * the wiki page explaining the check.
 */
@Serializable
data class HealthResource(
    val id: Int = 0,
    val source: String? = null,
    val type: HealthCheckResult? = null,
    val message: String? = null,
    val wikiUrl: String? = null,
)
