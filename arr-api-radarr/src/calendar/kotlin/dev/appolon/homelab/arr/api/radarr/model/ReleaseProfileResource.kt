package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * A release profile's must-contain and must-not-contain term lists. The spec
 * leaves `required` and `ignored` with an empty schema; Radarr sends them as
 * newline-delimited strings, so they pass through as strings.
 */
@Serializable
data class ReleaseProfileResource(
    val id: Int = 0,
    val name: String? = null,
    val enabled: Boolean = false,
    val required: String? = null,
    val ignored: String? = null,
    val indexerId: Int = 0,
    val tags: Set<Int>? = null,
)
