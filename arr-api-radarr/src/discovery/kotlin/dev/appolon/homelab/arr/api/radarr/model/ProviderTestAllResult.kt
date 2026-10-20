package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ProviderTestAllResult.
 * Dates remain unparsed strings. */
@Serializable
data class ProviderTestAllResult(
    val id: Int = 0,
    val isValid: Boolean = false,
    val validationFailures: List<ValidationFailure>? = null,
)
