package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * Custom format badge attached to a movie file. Movie screens only read the
 * identity fields; editing custom formats belongs to the CustomFormat tag task
 * in this module, which owns the full editor surface.
 */
@Serializable
data class CustomFormatResource(
    val id: Int = 0,
    val name: String? = null,
    val includeCustomFormatWhenRenaming: Boolean? = null,
    val specifications: List<CustomFormatSpecificationSchema>? = null,
)

/**
 * One condition inside a custom format. Recursive through `presets`; UI tests
 * and badge rendering never need to walk it, so it is carried verbatim.
 */
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
