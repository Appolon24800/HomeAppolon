package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class MonitoringOptions(
    val ignoreEpisodesWithFiles: Boolean = false,
    val ignoreEpisodesWithoutFiles: Boolean = false,
    val monitor: MonitorTypes = MonitorTypes.UNKNOWN
)
