package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr MovieCollection.
 * Dates remain unparsed strings. */
@Serializable
data class MovieCollection(
    val id: Int = 0,
    val title: String? = null,
    val cleanTitle: String? = null,
    val sortTitle: String? = null,
    val tmdbId: Int = 0,
    val overview: String? = null,
    val monitored: Boolean = false,
    val qualityProfileId: Int = 0,
    val rootFolderPath: String? = null,
    val searchOnAdd: Boolean = false,
    val minimumAvailability: MovieStatusType? = null,
    val lastInfoSync: String? = null,
    val images: List<MediaCover>? = null,
    val added: String = "",
    val movies: List<MovieMetadata>? = null,
    val tags: Set<Int>? = null,
)
