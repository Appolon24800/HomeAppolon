package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ReleaseEpisodeResource wire contract. Dates remain unparsed strings. */
@Serializable
data class ReleaseEpisodeResource(
    val id: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val absoluteEpisodeNumber: Int? = null,
    val title: String? = null
)
