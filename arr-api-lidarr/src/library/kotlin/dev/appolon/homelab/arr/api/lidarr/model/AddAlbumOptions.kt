package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr AddAlbumOptions wire resource. Dates remain unparsed strings. */
@Serializable
data class AddAlbumOptions(
    val addType: AlbumAddType = AlbumAddType.AUTOMATIC,
    val searchForNewAlbum: Boolean = false,
)
