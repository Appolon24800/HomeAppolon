package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class SeriesStatisticsResource(
    val seasonCount: Int = 0,
    val episodeFileCount: Int = 0,
    val episodeCount: Int = 0,
    val totalEpisodeCount: Int = 0,
    val sizeOnDisk: Long = 0L,
    val releaseGroups: List<String>? = null,
    val percentOfEpisodes: Double = 0.0
)
