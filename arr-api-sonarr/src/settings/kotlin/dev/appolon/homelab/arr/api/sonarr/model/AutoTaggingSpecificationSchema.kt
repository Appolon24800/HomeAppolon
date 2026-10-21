package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** One condition inside an AutoTaggingResource. */
@Serializable
data class AutoTaggingSpecificationSchema(
    val id: Int = 0,
    val name: String? = null,
    val implementation: String? = null,
    val implementationName: String? = null,
    val negate: Boolean = false,
    val required: Boolean = false,
    val fields: List<Field>? = null
)
