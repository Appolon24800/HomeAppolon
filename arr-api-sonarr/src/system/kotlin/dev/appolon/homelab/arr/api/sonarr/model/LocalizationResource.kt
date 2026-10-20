package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/**
 * Sonarr LocalizationResource wire contract. The spec types `strings` as an
 * object of nullable string values keyed by translation identifier.
 */
@Serializable
data class LocalizationResource(
    val id: Int = 0,
    val strings: Map<String, String?>? = null
)
