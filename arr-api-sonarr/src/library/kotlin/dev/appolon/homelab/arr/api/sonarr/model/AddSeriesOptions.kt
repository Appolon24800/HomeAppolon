package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class AddSeriesOptions(
    val ignoreEpisodesWithFiles: Boolean = false,
    val ignoreEpisodesWithoutFiles: Boolean = false,
    val monitor: MonitorTypes = MonitorTypes.UNKNOWN,
    val searchForMissingEpisodes: Boolean = false,
    val searchForCutoffUnmetEpisodes: Boolean = false
)
