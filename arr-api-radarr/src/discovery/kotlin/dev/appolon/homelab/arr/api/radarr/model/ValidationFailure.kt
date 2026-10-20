package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Wire contract for Radarr ValidationFailure.
 * Dates remain unparsed strings. */
@Serializable
data class ValidationFailure(
    val propertyName: String? = null,
    val errorMessage: String? = null,
    val attemptedValue: JsonElement? = null,
    val customState: JsonElement? = null,
    val severity: Severity? = null,
    val errorCode: String? = null,
    val formattedMessageArguments: List<JsonElement>? = null,
    val formattedMessagePlaceholderValues: Map<String, JsonElement>? = null,
)
