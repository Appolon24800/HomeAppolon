package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr DiskSpaceResource wire contract. Sizes are bytes. */
@Serializable
data class DiskSpaceResource(
    val id: Int = 0,
    val path: String? = null,
    val label: String? = null,
    val freeSpace: Long = 0,
    val totalSpace: Long = 0
)
