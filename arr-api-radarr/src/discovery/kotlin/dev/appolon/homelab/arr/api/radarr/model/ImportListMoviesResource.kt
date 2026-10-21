package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ImportListMoviesResource.
 * Dates remain unparsed strings. */
@Serializable
data class ImportListMoviesResource(
    val id: Int = 0,
    val title: String? = null,
    val sortTitle: String? = null,
    val originalLanguage: Language? = null,
    val status: MovieStatusType? = null,
    val overview: String? = null,
    val inCinemas: String? = null,
    val physicalRelease: String? = null,
    val digitalRelease: String? = null,
    val images: List<MediaCover>? = null,
    val website: String? = null,
    val remotePoster: String? = null,
    val year: Int = 0,
    val youTubeTrailerId: String? = null,
    val studio: String? = null,
    val runtime: Int = 0,
    val imdbId: String? = null,
    val tmdbId: Int = 0,
    val folder: String? = null,
    val certification: String? = null,
    val popularity: Double = 0.0,
    val genres: List<String>? = null,
    val ratings: Ratings? = null,
    val collection: MovieCollection? = null,
    val isExcluded: Boolean = false,
    val isExisting: Boolean = false,
    val isTrending: Boolean = false,
    val isPopular: Boolean = false,
    val isRecommendation: Boolean = false,
    val lists: Set<Int>? = null,
)
