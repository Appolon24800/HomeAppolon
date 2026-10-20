package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Provider settings field descriptor. Only reached from movie files through
 * custom formats; `value` is schema-free on the wire (any JSON), so it passes
 * through as a raw element rather than a typed guess.
 */
@Serializable
data class Field(
    val order: Int = 0,
    val name: String? = null,
    val label: String? = null,
    val unit: String? = null,
    val helpText: String? = null,
    val helpTextWarning: String? = null,
    val helpLink: String? = null,
    val value: JsonElement? = null,
    val type: String? = null,
    val advanced: Boolean = false,
    val selectOptions: List<SelectOption>? = null,
    val selectOptionsProviderAction: String? = null,
    val section: String? = null,
    val hidden: String? = null,
    val privacy: PrivacyLevel? = null,
    val placeholder: String? = null,
    val isFloat: Boolean = false,
)

@Serializable
data class SelectOption(
    val value: Int = 0,
    val name: String? = null,
    val order: Int = 0,
    val hint: String? = null,
    val dividerAfter: Boolean = false,
)

@Serializable
enum class PrivacyLevel {
    @SerialName("normal") NORMAL,
    @SerialName("password") PASSWORD,
    @SerialName("apiKey") API_KEY,
    @SerialName("userName") USER_NAME,
}
