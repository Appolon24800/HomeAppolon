package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr HistoryResource. Dates remain unparsed strings. */
@Serializable
data class HistoryResource(
    val id: Int = 0,
    val movieId: Int = 0,
    val sourceTitle: String? = null,
    val languages: List<Language>? = null,
    val quality: QualityModel = QualityModel(),
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
    val qualityCutoffNotMet: Boolean = false,
    val date: String = "",
    val downloadId: String? = null,
    val eventType: MovieHistoryEventType = MovieHistoryEventType.UNKNOWN,
    val data: Map<String, String?>? = null,
    val movie: MovieResource = MovieResource(),
)
