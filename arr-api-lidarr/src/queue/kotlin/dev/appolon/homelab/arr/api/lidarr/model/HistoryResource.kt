package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class HistoryResource(
    val id: Int = 0,
    val albumId: Int = 0,
    val artistId: Int = 0,
    val trackId: Int = 0,
    val sourceTitle: String? = null,
    val quality: QualityModel? = null,
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
    val qualityCutoffNotMet: Boolean = false,
    val date: String = "",
    val downloadId: String? = null,
    val eventType: EntityHistoryEventType = EntityHistoryEventType.UNKNOWN,
    val data: Map<String, String?>? = null,
    val album: AlbumResource? = null,
    val artist: ArtistResource? = null,
    val track: TrackResource? = null,
)
