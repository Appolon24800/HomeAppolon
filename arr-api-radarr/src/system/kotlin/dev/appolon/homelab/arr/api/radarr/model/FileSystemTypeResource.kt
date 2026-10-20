package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr FileSystemTypeResource. Dates remain unparsed strings. */
@Serializable
data class FileSystemTypeResource(
    val type: FileSystemEntityType? = null,
)
