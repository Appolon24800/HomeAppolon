package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr AlbumsMonitoredResource wire resource. Dates remain unparsed strings. */
@Serializable
data class AlbumsMonitoredResource(
    val albumIds: List<Int>? = null,
    val monitored: Boolean = false,
)
