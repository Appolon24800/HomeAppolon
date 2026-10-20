package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr AlternativeTitle.
 * Dates remain unparsed strings. */
@Serializable
data class AlternativeTitle(
    val id: Int = 0,
    val sourceType: SourceType? = null,
    val movieMetadataId: Int = 0,
    val title: String? = null,
    val cleanTitle: String? = null,
)
