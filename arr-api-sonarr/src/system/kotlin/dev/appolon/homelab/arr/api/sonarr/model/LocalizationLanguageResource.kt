package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr LocalizationLanguageResource wire contract. */
@Serializable
data class LocalizationLanguageResource(
    val identifier: String? = null
)
