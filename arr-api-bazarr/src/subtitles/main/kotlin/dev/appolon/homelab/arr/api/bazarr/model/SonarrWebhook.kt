package dev.appolon.homelab.arr.api.bazarr.model

import kotlinx.serialization.Serializable

@Serializable
data class SonarrWebhook(
    val eventType: String,
    val episodes: List<SonarrEpisode>? = null,
    val episodeFiles: List<SonarrEpisodeFile>? = null,
)
