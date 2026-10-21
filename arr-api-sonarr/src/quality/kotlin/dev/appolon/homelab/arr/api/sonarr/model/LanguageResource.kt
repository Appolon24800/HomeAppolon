package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr LanguageResource wire contract, the language lookup list. */
@Serializable
data class LanguageResource(
    val id: Int = 0,
    val name: String? = null,
    val nameLower: String? = null
)
