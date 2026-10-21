package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class SeasonPassSeriesResource(
    val id: Int = 0,
    val monitored: Boolean? = null,
    val seasons: List<SeasonResource>? = null
)
