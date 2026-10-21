package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * Row of the language table served by the Language tag. Distinct from
 * [Language], the trimmed id-plus-name reference embedded inside movie and
 * profile resources.
 */
@Serializable
data class LanguageResource(
    val id: Int = 0,
    val name: String? = null,
    val nameLower: String? = null,
)
