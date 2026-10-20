package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr CollectionMovieResource.
 * Dates remain unparsed strings. */
@Serializable
data class CollectionMovieResource(
    val tmdbId: Int = 0,
    val imdbId: String? = null,
    val title: String? = null,
    val cleanTitle: String? = null,
    val sortTitle: String? = null,
    val status: MovieStatusType? = null,
    val overview: String? = null,
    val runtime: Int = 0,
    val images: List<MediaCover>? = null,
    val year: Int = 0,
    val ratings: Ratings? = null,
    val genres: List<String>? = null,
    val folder: String? = null,
    val isExisting: Boolean = false,
    val isExcluded: Boolean = false,
)
