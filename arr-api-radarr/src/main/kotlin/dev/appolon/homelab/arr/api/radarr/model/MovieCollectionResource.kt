package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * Collection reference embedded in a movie. The full collection editor lives
 * under the Collection tag.
 */
@Serializable
data class MovieCollectionResource(
    val title: String? = null,
    val tmdbId: Int = 0,
)
