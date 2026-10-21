package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr MediumResource wire resource. Dates remain unparsed strings. */
@Serializable
data class MediumResource(
    val mediumNumber: Int = 0,
    val mediumName: String? = null,
    val mediumFormat: String? = null,
)
