package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr TagDifference wire resource. Dates remain unparsed strings. */
@Serializable
data class TagDifference(
    val field: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
)
