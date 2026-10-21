package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ApiInfoResource wire contract. */
@Serializable
data class ApiInfoResource(
    val current: String? = null,
    val deprecated: List<String>? = null
)
