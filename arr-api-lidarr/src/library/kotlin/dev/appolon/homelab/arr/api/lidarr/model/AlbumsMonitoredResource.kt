package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr AlbumsMonitoredResource wire resource. Dates remain unparsed strings. */
@Serializable
data class AlbumsMonitoredResource(
    val albumIds: List<Int>? = null,
    val monitored: Boolean = false,
)
