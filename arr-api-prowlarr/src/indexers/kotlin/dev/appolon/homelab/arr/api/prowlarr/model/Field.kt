package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Visibility of a provider [Field] value in UI and diagnostics, from the spec's
 * PrivacyLevel enum. Entries with PASSWORD or API_KEY must never render in
 * plain text or reach logs.
 */
@Serializable
enum class PrivacyLevel {
    @SerialName("normal") NORMAL,
    @SerialName("password") PASSWORD,
    @SerialName("apiKey") API_KEY,
    @SerialName("userName") USER_NAME,
}

/**
 * One row of a provider's dynamic settings form. Prowlarr describes provider
 * settings as data instead of typed DTOs, so [value] keeps whatever JSON the
 * server sent (bool, number, string, array or object) and the UI picks a
 * renderer from [type]. [selectOptions] is non-null only for select-type rows.
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
    val privacy: PrivacyLevel = PrivacyLevel.NORMAL,
    val placeholder: String? = null,
    val isFloat: Boolean = false,
)

/**
 * One choice of a select-type [Field]. The spec names this schema SelectOption;
 * it is modeled under its wire name because every provider resource references
 * it from `fields[].selectOptions`.
 */
@Serializable
data class SelectOption(
    val value: Int = 0,
    val name: String? = null,
    val order: Int = 0,
    val hint: String? = null,
    val parentValue: Int? = null,
)
