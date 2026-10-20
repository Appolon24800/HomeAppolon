package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Sonarr LocalizationResource wire contract. `strings` is a free-form
 * message catalog keyed by translation identifier, so it passes through as
 * raw JSON.
 */
@Serializable
data class LocalizationResource(
    val id: Int = 0,
    val strings: JsonObject? = null
)
