package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr LanguageProfileItemResource wire contract. */
@Serializable
data class LanguageProfileItemResource(
    val id: Int = 0,
    val language: Language = Language(),
    val allowed: Boolean = false
)
