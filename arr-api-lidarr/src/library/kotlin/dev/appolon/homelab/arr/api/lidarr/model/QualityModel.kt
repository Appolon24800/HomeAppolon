package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr QualityModel wire resource. Dates remain unparsed strings. */
@Serializable
data class QualityModel(
    val quality: Quality? = null,
    val revision: Revision? = null,
)
