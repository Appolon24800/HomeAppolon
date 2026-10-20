package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr RemotePathMappingResource wire resource for one path rewrite. */
@Serializable
data class RemotePathMappingResource(
    val id: Int = 0,
    val host: String? = null,
    val remotePath: String? = null,
    val localPath: String? = null,
)
