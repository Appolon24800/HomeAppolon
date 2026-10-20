package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr Revision wire resource. Dates remain unparsed strings. */
@Serializable
data class Revision(
    val version: Int = 0,
    val real: Int = 0,
    val isRepack: Boolean = false,
)
