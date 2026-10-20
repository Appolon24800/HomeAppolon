package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Sonarr BlocklistResource wire contract. Dates remain unparsed strings. */
@Serializable
data class BlocklistResource(
    val id: Int = 0,
    val seriesId: Int = 0,
    val episodeIds: List<Int>? = null,
    val sourceTitle: String? = null,
    val languages: List<Language>? = null,
    val quality: QualityModel = QualityModel(),
    val customFormats: List<CustomFormatResource>? = null,
    val date: String = "1970-01-01T00:00:00Z",
    val protocol: DownloadProtocol = DownloadProtocol.UNKNOWN,
    val indexer: String? = null,
    val message: String? = null,
    val series: JsonObject = JsonObject(emptyMap())
)
