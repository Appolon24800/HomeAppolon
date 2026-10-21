package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr ReleaseStatus wire resource. The spec does not enumerate values. */
@Serializable
data class ReleaseStatus(
    val id: Int = 0,
    val name: String? = null,
)
