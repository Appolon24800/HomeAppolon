package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ParseResource. Dates remain unparsed strings. */
@Serializable
data class ParseResource(
    val id: Int = 0,
    val title: String? = null,
    val parsedMovieInfo: ParsedMovieInfo? = null,
    val movie: MovieResource? = null,
    val languages: List<Language>? = null,
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
)
