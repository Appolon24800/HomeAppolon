package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One user-saved filter preset for a screen. The spec leaves each entry of
 * [filters] free-form (`additionalProperties: {}`), so entries pass through
 * as [JsonObject] and screens interpret them per filter type.
 */
@Serializable
data class CustomFilterResource(
    val id: Int = 0,
    val type: String? = null,
    val label: String? = null,
    val filters: List<JsonObject>? = null,
)
