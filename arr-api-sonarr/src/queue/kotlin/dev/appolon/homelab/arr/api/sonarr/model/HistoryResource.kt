package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr HistoryResource wire contract. Dates remain unparsed strings. */
@Serializable
data class HistoryResource(
    val id: Int = 0,
    val episodeId: Int = 0,
    val seriesId: Int = 0,
    val sourceTitle: String? = null,
    val languages: List<Language>? = null,
    val quality: QualityModel = QualityModel(),
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
    val qualityCutoffNotMet: Boolean = false,
    val date: String = "1970-01-01T00:00:00Z",
    val downloadId: String? = null,
    val eventType: EpisodeHistoryEventType = EpisodeHistoryEventType.UNKNOWN,
    val data: Map<String, String?>? = null,
    val episode: EpisodeResource = EpisodeResource(),
    val series: SeriesResource = SeriesResource()
)
