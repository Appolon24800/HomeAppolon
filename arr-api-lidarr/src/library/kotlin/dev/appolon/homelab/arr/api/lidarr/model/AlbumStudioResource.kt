package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr AlbumStudioResource wire resource. Dates remain unparsed strings. */
@Serializable
data class AlbumStudioResource(
    val artist: List<AlbumStudioArtistResource>? = null,
    val monitoringOptions: MonitoringOptions? = null,
    val monitorNewItems: NewItemMonitorTypes = NewItemMonitorTypes.ALL,
)
