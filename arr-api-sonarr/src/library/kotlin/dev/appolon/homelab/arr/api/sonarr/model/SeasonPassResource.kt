package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class SeasonPassResource(
    val series: List<SeasonPassSeriesResource>? = null,
    val monitoringOptions: MonitoringOptions = MonitoringOptions()
)
