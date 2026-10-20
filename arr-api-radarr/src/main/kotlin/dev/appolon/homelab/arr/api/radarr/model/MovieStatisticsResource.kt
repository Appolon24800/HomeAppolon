package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Aggregated file statistics Radarr computes per movie. */
@Serializable
data class MovieStatisticsResource(
    val movieFileCount: Int = 0,
    val sizeOnDisk: Long = 0L,
    val releaseGroups: List<String>? = null,
    val movieFileQualities: List<Quality>? = null,
)
