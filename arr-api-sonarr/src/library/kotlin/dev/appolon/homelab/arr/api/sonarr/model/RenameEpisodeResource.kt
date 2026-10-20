package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class RenameEpisodeResource(
    val id: Int = 0,
    val seriesId: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumbers: List<Int>? = null,
    val episodeFileId: Int = 0,
    val existingPath: String? = null,
    val newPath: String? = null
)
