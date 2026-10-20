package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr RemotePathMappingResource.
 * Dates remain unparsed strings. */
@Serializable
data class RemotePathMappingResource(
    val id: Int = 0,
    val host: String? = null,
    val remotePath: String? = null,
    val localPath: String? = null,
)
