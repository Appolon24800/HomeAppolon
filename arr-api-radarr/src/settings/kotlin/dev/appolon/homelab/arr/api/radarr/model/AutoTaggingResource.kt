package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr AutoTaggingResource. Dates remain unparsed strings. */
@Serializable
data class AutoTaggingResource(
    val id: Int = 0,
    val name: String? = null,
    val removeTagsAutomatically: Boolean = false,
    val tags: Set<Int>? = null,
    val specifications: List<AutoTaggingSpecificationSchema>? = null,
)

/** One condition of an auto tagging profile; operators are schema-driven through [fields]. */
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
