package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Maps a path as the download client host reports it to a path Sonarr can read. */
@Serializable
data class RemotePathMappingResource(
    val id: Int = 0,
    val host: String? = null,
    val remotePath: String? = null,
    val localPath: String? = null
)
