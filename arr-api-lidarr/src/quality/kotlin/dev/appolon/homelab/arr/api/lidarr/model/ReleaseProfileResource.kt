package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr ReleaseProfileResource wire resource. Dates remain unparsed strings. */
@Serializable
data class ReleaseProfileResource(
    val id: Int = 0,
    val enabled: Boolean = false,
    val required: List<String>? = null,
    val ignored: List<String>? = null,
    val indexerId: Int = 0,
    val tags: List<Int>? = null,
)
