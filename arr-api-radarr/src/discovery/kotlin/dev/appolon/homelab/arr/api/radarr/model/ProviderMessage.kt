package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ProviderMessage.
 * Dates remain unparsed strings. */
@Serializable
data class ProviderMessage(
    val message: String? = null,
    val type: ProviderMessageType? = null,
)
