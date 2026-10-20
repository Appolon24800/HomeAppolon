package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr AddAlbumOptions wire resource. Dates remain unparsed strings. */
@Serializable
data class AddAlbumOptions(
    val addType: AlbumAddType = AlbumAddType.AUTOMATIC,
    val searchForNewAlbum: Boolean = false,
)
