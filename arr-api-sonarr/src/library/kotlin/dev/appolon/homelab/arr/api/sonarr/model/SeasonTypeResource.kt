package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class SeasonTypeResource(
    val name: String? = null,
    val type: String? = null,
    val seasonNumbers: List<Int>? = null,
    val episodeCount: Int = 0
)
