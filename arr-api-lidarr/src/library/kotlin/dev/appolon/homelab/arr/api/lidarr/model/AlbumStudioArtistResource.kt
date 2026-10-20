package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr AlbumStudioArtistResource wire resource. Dates remain unparsed strings. */
@Serializable
data class AlbumStudioArtistResource(
    val id: Int = 0,
    val monitored: Boolean? = null,
    val albums: List<AlbumResource>? = null,
)
