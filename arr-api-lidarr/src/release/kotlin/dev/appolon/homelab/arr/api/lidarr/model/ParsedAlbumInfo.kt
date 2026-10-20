package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class ParsedAlbumInfo(
    val releaseTitle: String? = null,
    val albumTitle: String? = null,
    val artistName: String? = null,
    val albumType: String? = null,
    val artistTitleInfo: ArtistTitleInfo? = null,
    val quality: QualityModel? = null,
    val releaseDate: String? = null,
    val discography: Boolean = false,
    val discographyStart: Int = 0,
    val discographyEnd: Int = 0,
    val releaseGroup: String? = null,
    val releaseHash: String? = null,
    val releaseVersion: String? = null,
)
