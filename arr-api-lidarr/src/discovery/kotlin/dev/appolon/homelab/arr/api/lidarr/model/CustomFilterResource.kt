package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Lidarr CustomFilterResource wire resource. The spec types each filter as a free
 * form object, so filters pass through as maps the UI interprets per filter type.
 */
@Serializable
data class CustomFilterResource(
    val id: Int = 0,
    val type: String? = null,
    val label: String? = null,
    val filters: List<Map<String, JsonElement>>? = null,
)
