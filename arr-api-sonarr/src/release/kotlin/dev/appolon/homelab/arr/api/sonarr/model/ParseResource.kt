package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Sonarr ParseResource wire contract. Dates remain unparsed strings. */
@Serializable
data class ParseResource(
    val id: Int = 0,
    val title: String? = null,
    val parsedEpisodeInfo: ParsedEpisodeInfoResource = ParsedEpisodeInfoResource(),
    val series: JsonObject = JsonObject(emptyMap()),
    val episodes: List<JsonObject>? = null,
    val languages: List<Language>? = null,
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0
)
