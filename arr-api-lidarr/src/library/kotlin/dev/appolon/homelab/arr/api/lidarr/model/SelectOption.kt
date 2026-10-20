package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr SelectOption wire resource. Dates remain unparsed strings. */
@Serializable
data class SelectOption(
    val value: Int = 0,
    val name: String? = null,
    val order: Int = 0,
    val hint: String? = null,
)
