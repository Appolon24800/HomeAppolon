package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * Language reference embedded in movie files and movie resources. Radarr only
 * ever sends `id` plus `name` here; the full language table lives behind the
 * Language tag which is owned by another task in this module.
 */
@Serializable
data class Language(
    val id: Int = 0,
    val name: String? = null,
)
