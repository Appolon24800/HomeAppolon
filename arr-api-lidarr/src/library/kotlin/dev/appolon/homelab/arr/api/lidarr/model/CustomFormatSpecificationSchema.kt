package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr CustomFormatSpecificationSchema wire resource. Dates remain unparsed strings. */
@Serializable
data class CustomFormatSpecificationSchema(
    val id: Int = 0,
    val name: String? = null,
    val implementation: String? = null,
    val implementationName: String? = null,
    val infoLink: String? = null,
    val negate: Boolean = false,
    val required: Boolean = false,
    val fields: List<Field>? = null,
    val presets: List<CustomFormatSpecificationSchema>? = null,
)
