package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr NamingConfigResource wire resource for rename settings and formats. */
@Serializable
data class NamingConfigResource(
    val id: Int = 0,
    val renameTracks: Boolean = false,
    val replaceIllegalCharacters: Boolean = false,
    val colonReplacementFormat: Int = 0,
    val standardTrackFormat: String? = null,
    val multiDiscTrackFormat: String? = null,
    val artistFolderFormat: String? = null,
    val includeArtistName: Boolean = false,
    val includeAlbumTitle: Boolean = false,
    val includeQuality: Boolean = false,
    val replaceSpaces: Boolean = false,
    val separator: String? = null,
    val numberStyle: String? = null,
)
