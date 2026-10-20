package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Per-app sync defaults that Prowlarr applies to indexers routed to a given
 * application. Returned as a single template by the schema endpoint.
 */
@Serializable
data class AppProfileResource(
    val id: Int = 0,
    val name: String? = null,
    val enableRss: Boolean = false,
    val enableAutomaticSearch: Boolean = false,
    val enableInteractiveSearch: Boolean = false,
    val minimumSeeders: Int = 0,
)
