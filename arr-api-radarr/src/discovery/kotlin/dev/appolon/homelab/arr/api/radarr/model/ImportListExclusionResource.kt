package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ImportListExclusionResource.
 * Dates remain unparsed strings. */
@Serializable
data class ImportListExclusionResource(
    val id: Int = 0,
    val name: String? = null,
    val fields: List<Field>? = null,
    val implementationName: String? = null,
    val implementation: String? = null,
    val configContract: String? = null,
    val infoLink: String? = null,
    val message: ProviderMessage? = null,
    val tags: Set<Int>? = null,
    val presets: List<ImportListExclusionResource>? = null,
    val tmdbId: Int = 0,
    val movieTitle: String? = null,
    val movieYear: Int = 0,
)
