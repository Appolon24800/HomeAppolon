package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class SeasonResource(
    val seasonNumber: Int = 0,
    val monitored: Boolean = false,
    val title: String? = null,
    val statistics: SeasonStatisticsResource = SeasonStatisticsResource(),
    val images: List<MediaCover>? = null
)
