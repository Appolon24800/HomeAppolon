package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class BlocklistResource(
    val id: Int = 0,
    val artistId: Int = 0,
    val albumIds: List<Int>? = null,
    val sourceTitle: String? = null,
    val quality: QualityModel? = null,
    val customFormats: List<CustomFormatResource>? = null,
    val date: String = "",
    val protocol: DownloadProtocol = DownloadProtocol.UNKNOWN,
    val indexer: String? = null,
    val message: String? = null,
    val artist: ArtistResource? = null,
)
