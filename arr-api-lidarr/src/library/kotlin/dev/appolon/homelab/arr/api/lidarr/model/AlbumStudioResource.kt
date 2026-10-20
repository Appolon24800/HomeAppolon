package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr AlbumStudioResource wire resource. Dates remain unparsed strings. */
@Serializable
data class AlbumStudioResource(
    val artist: List<AlbumStudioArtistResource>? = null,
    val monitoringOptions: MonitoringOptions? = null,
    val monitorNewItems: NewItemMonitorTypes = NewItemMonitorTypes.ALL,
)
