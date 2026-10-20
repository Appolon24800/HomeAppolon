package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr TagResource. Dates remain unparsed strings. */
@Serializable
data class TagResource(
    val id: Int = 0,
    val label: String? = null,
)
