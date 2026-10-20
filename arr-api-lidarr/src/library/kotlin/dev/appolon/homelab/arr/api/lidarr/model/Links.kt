package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr Links wire resource. Dates remain unparsed strings. */
@Serializable
data class Links(
    val url: String? = null,
    val name: String? = null,
)
