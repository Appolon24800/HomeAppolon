package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class EpisodesMonitoredResource(
    val episodeIds: List<Int>? = null,
    val monitored: Boolean = false
)
