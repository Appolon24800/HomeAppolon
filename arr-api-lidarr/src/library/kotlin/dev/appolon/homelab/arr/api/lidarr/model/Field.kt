package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr Field wire resource. Dates remain unparsed strings. */
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
