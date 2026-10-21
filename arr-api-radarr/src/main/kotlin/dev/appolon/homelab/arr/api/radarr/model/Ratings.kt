package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Per-source rating bundle on every movie resource. */
@Serializable
data class Ratings(
    val imdb: RatingChild? = null,
    val tmdb: RatingChild? = null,
    val metacritic: RatingChild? = null,
    val rottenTomatoes: RatingChild? = null,
    val trakt: RatingChild? = null,
)

@Serializable
data class RatingChild(
    val votes: Int = 0,
    val value: Double = 0.0,
    val type: RatingType? = null,
)

@Serializable
enum class RatingType {
    @SerialName("user") USER,
    @SerialName("critic") CRITIC,
}
