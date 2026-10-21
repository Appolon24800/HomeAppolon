package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Result of Radarr's release-title parser, the payload behind `/api/v3/parse`. */
@Serializable
data class ParsedMovieInfo(
    val movieTitles: List<String>? = null,
    val originalTitle: String? = null,
    val releaseTitle: String? = null,
    val simpleReleaseTitle: String? = null,
    val quality: QualityModel? = null,
    val languages: List<Language>? = null,
    val releaseGroup: String? = null,
    val releaseHash: String? = null,
    val edition: String? = null,
    val year: Int = 0,
    val imdbId: String? = null,
    val tmdbId: Int = 0,
    val hardcodedSubs: String? = null,
    val movieTitle: String? = null,
    val primaryMovieTitle: String? = null,
)
