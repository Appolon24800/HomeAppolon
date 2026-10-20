package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr AddArtistOptions wire resource. Dates remain unparsed strings. */
@Serializable
data class AddArtistOptions(
    val monitor: MonitorTypes = MonitorTypes.ALL,
    val albumsToMonitor: List<String>? = null,
    val monitored: Boolean = false,
    val searchForMissingAlbums: Boolean = false,
)
