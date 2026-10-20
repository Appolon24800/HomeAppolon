package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr LanguageProfileResource wire contract. */
@Serializable
data class LanguageProfileResource(
    val id: Int = 0,
    val name: String? = null,
    val upgradeAllowed: Boolean = false,
    val cutoff: Language = Language(),
    val languages: List<LanguageProfileItemResource>? = null
)
