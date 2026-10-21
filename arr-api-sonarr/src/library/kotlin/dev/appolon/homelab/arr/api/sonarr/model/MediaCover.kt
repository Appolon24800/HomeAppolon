package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaCover(
    val coverType: MediaCoverTypes = MediaCoverTypes.UNKNOWN,
    val url: String? = null,
    val remoteUrl: String? = null
)
