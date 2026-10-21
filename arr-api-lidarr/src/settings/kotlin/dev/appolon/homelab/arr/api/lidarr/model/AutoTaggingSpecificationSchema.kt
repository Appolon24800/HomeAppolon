package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/**
 * Lidarr AutoTaggingSpecificationSchema wire resource: one condition inside an
 * auto tagging rule. Reuses [Field] from the library group.
 */
@Serializable
data class AutoTaggingSpecificationSchema(
    val id: Int = 0,
    val name: String? = null,
    val implementation: String? = null,
    val implementationName: String? = null,
    val negate: Boolean = false,
    val required: Boolean = false,
    val fields: List<Field>? = null,
)
