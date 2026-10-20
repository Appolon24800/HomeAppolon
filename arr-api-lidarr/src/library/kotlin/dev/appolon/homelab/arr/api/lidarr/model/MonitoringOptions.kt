package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr MonitoringOptions wire resource. Dates remain unparsed strings. */
@Serializable
data class MonitoringOptions(
    val monitor: MonitorTypes = MonitorTypes.ALL,
    val albumsToMonitor: List<String>? = null,
    val monitored: Boolean = false,
)
