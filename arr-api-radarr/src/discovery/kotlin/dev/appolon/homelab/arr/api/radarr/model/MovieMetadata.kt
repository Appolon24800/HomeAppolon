package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr MovieMetadata.
 * Dates remain unparsed strings. */
@Serializable
data class MovieMetadata(
    val id: Int = 0,
    val tmdbId: Int = 0,
    val images: List<MediaCover>? = null,
    val genres: List<String>? = null,
    val keywords: List<String>? = null,
    val inCinemas: String? = null,
    val physicalRelease: String? = null,
    val digitalRelease: String? = null,
    val certification: String? = null,
    val year: Int = 0,
    val ratings: Ratings? = null,
    val collectionTmdbId: Int = 0,
    val collectionTitle: String? = null,
    val lastInfoSync: String? = null,
    val runtime: Int = 0,
    val website: String? = null,
    val imdbId: String? = null,
    val title: String? = null,
    val cleanTitle: String? = null,
    val sortTitle: String? = null,
    val status: MovieStatusType? = null,
    val overview: String? = null,
    val alternativeTitles: List<AlternativeTitle>? = null,
    val translations: List<MovieTranslation>? = null,
    val secondaryYear: Int? = null,
    val youTubeTrailerId: String? = null,
    val studio: String? = null,
    val originalTitle: String? = null,
    val cleanOriginalTitle: String? = null,
    val originalLanguage: Language? = null,
    val recommendations: List<Int>? = null,
    val popularity: Double = 0.0,
    val isRecentMovie: Boolean = false,
)
