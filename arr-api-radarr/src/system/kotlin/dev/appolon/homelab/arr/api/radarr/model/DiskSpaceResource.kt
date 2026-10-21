package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr DiskSpaceResource. Dates remain unparsed strings. */
@Serializable
data class DiskSpaceResource(
    val id: Int = 0,
    val path: String? = null,
    val label: String? = null,
    val freeSpace: Long = 0,
    val totalSpace: Long = 0,
)
