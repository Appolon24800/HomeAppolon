package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr BlocklistResource. Dates remain unparsed strings. */
@Serializable
data class BlocklistResource(
    val id: Int = 0,
    val movieId: Int = 0,
    val sourceTitle: String? = null,
    val languages: List<Language>? = null,
    val quality: QualityModel = QualityModel(),
    val customFormats: List<CustomFormatResource>? = null,
    val date: String = "",
    val protocol: DownloadProtocol = DownloadProtocol.UNKNOWN,
    val indexer: String? = null,
    val message: String? = null,
    val movie: MovieResource = MovieResource(),
)
