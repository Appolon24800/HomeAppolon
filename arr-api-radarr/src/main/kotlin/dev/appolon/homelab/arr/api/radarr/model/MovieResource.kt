package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The central Radarr movie resource, used by list, editor, import, and lookup
 * endpoints alike. Dates are ISO-8601 strings passed through untouched; the UI
 * parses them with `:arr-core` ArrTime.
 *
 * Modeling convention for this file (and the module): scalar and array fields
 * keep the spec's nullability, object and enum references are nullable because
 * OpenAPI 3.0 cannot mark nullability on a `$ref` sibling and Radarr omits
 * them when absent. Every field carries a default so partial server payloads
 * and queued offline actions round-trip without MissingFieldException.
 */
@Serializable
data class MovieResource(
    val id: Int = 0,
    val title: String? = null,
    val originalTitle: String? = null,
    val originalLanguage: Language? = null,
    val alternateTitles: List<AlternativeTitleResource>? = null,
    val secondaryYear: Int? = null,
    val secondaryYearSourceId: Int = 0,
    val sortTitle: String? = null,
    val sizeOnDisk: Long? = null,
    val status: MovieStatusType? = null,
    val overview: String? = null,
    val inCinemas: String? = null,
    val physicalRelease: String? = null,
    val digitalRelease: String? = null,
    val releaseDate: String? = null,
    val physicalReleaseNote: String? = null,
    val images: List<MediaCover>? = null,
    val website: String? = null,
    val remotePoster: String? = null,
    val year: Int = 0,
    val youTubeTrailerId: String? = null,
    val studio: String? = null,
    val path: String? = null,
    val qualityProfileId: Int = 0,
    val hasFile: Boolean? = null,
    val movieFileId: Int = 0,
    val monitored: Boolean = false,
    val minimumAvailability: MovieStatusType? = null,
    val isAvailable: Boolean = false,
    val folderName: String? = null,
    val runtime: Int = 0,
    val cleanTitle: String? = null,
    val imdbId: String? = null,
    val tmdbId: Int = 0,
    val titleSlug: String? = null,
    val rootFolderPath: String? = null,
    val folder: String? = null,
    val certification: String? = null,
    val genres: List<String>? = null,
    val keywords: List<String>? = null,
    val tags: Set<Int>? = null,
    val added: String? = null,
    val addOptions: AddMovieOptions? = null,
    val ratings: Ratings? = null,
    val movieFile: MovieFileResource? = null,
    val collection: MovieCollectionResource? = null,
    val popularity: Float = 0f,
    val lastSearchTime: String? = null,
    val statistics: MovieStatisticsResource? = null,
)

/** Add-time options posted inside a MovieResource when adding a new movie. */
@Serializable
data class AddMovieOptions(
    val ignoreEpisodesWithFiles: Boolean = false,
    val ignoreEpisodesWithoutFiles: Boolean = false,
    val monitor: MonitorTypes? = null,
    val searchForMovie: Boolean = false,
    val addMethod: AddMovieMethod? = null,
)

@Serializable
enum class MovieStatusType {
    @SerialName("tba") TBA,
    @SerialName("announced") ANNOUNCED,
    @SerialName("inCinemas") IN_CINEMAS,
    @SerialName("released") RELEASED,
    @SerialName("deleted") DELETED,
}

@Serializable
enum class MonitorTypes {
    @SerialName("movieOnly") MOVIE_ONLY,
    @SerialName("movieAndCollection") MOVIE_AND_COLLECTION,
    @SerialName("none") NONE,
}

@Serializable
enum class AddMovieMethod {
    @SerialName("manual") MANUAL,
    @SerialName("list") LIST,
    @SerialName("collection") COLLECTION,
}
