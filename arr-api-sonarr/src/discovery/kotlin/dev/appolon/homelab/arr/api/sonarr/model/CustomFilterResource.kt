package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Sonarr CustomFilterResource wire contract. `filters` is an open list of filter
 * criteria objects whose shape varies per filter type, so it stays structured JSON.
 */
@Serializable
data class CustomFilterResource(
    val id: Int = 0,
    val type: String? = null,
    val label: String? = null,
    val filters: List<Map<String, JsonElement>>? = null
)
