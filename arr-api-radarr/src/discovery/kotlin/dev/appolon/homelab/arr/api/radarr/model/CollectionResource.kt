package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr CollectionResource.
 * Dates remain unparsed strings. */
@Serializable
data class CollectionResource(
    val id: Int = 0,
    val title: String? = null,
    val sortTitle: String? = null,
    val tmdbId: Int = 0,
    val images: List<MediaCover>? = null,
    val overview: String? = null,
    val monitored: Boolean = false,
    val rootFolderPath: String? = null,
    val qualityProfileId: Int = 0,
    val searchOnAdd: Boolean = false,
    val minimumAvailability: MovieStatusType? = null,
    val movies: List<CollectionMovieResource>? = null,
    val missingMovies: Int = 0,
    val tags: Set<Int>? = null,
)
