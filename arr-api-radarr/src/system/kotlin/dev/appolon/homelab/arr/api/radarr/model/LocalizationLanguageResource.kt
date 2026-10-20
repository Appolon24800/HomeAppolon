package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr LocalizationLanguageResource. Dates remain unparsed strings. */
@Serializable
data class LocalizationLanguageResource(
    val identifier: String? = null,
)
