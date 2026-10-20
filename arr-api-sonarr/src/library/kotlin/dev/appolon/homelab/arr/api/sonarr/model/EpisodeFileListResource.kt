package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class EpisodeFileListResource(
    val episodeFileIds: List<Int>? = null,
    val languages: List<Language>? = null,
    val quality: QualityModel = QualityModel(),
    val sceneName: String? = null,
    val releaseGroup: String? = null
)
