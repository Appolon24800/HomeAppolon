package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/** API versions the server exposes, from `GET /api`. */
@Serializable
data class ApiInfoResource(
    val current: String? = null,
    val deprecated: List<String>? = null,
)
