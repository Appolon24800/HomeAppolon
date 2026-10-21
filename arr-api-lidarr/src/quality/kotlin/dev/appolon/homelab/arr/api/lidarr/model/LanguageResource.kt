package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr LanguageResource wire resource. Dates remain unparsed strings. */
@Serializable
data class LanguageResource(
    val id: Int = 0,
    val name: String? = null,
    val nameLower: String? = null,
)
