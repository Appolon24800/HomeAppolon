package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class ParseResource(
    val id: Int = 0,
    val title: String? = null,
    val parsedAlbumInfo: ParsedAlbumInfo? = null,
    val artist: ArtistResource? = null,
    val albums: List<AlbumResource>? = null,
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
)
