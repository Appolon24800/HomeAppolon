package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr DiskSpaceResource wire resource for one mounted path. */
@Serializable
data class DiskSpaceResource(
    val id: Int = 0,
    val path: String? = null,
    val label: String? = null,
    val freeSpace: Long = 0,
    val totalSpace: Long = 0,
)
