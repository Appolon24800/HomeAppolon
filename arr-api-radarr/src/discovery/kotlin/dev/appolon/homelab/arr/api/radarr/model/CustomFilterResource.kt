package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Wire contract for Radarr CustomFilterResource.
 * Dates remain unparsed strings. */
@Serializable
data class CustomFilterResource(
    val id: Int = 0,
    val type: String? = null,
    val label: String? = null,
    val filters: List<Map<String, JsonElement>>? = null,
)
