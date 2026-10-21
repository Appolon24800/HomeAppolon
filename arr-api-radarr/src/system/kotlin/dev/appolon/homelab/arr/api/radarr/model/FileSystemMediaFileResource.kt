package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr FileSystemMediaFileResource. Dates remain unparsed strings. */
@Serializable
data class FileSystemMediaFileResource(
    val path: String? = null,
    val relativePath: String? = null,
    val name: String? = null,
)
