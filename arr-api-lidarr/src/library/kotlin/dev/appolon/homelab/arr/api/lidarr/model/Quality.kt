package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr Quality wire resource. Dates remain unparsed strings. */
@Serializable
data class Quality(
    val id: Int = 0,
    val name: String? = null,
)
