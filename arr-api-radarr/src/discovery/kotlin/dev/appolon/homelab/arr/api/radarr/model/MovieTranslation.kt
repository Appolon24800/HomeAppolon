package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr MovieTranslation.
 * Dates remain unparsed strings. */
@Serializable
data class MovieTranslation(
    val id: Int = 0,
    val movieMetadataId: Int = 0,
    val title: String? = null,
    val cleanTitle: String? = null,
    val overview: String? = null,
    val language: Language? = null,
)
