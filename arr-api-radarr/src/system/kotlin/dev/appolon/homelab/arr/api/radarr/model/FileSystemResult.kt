package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr FileSystemResult. Dates remain unparsed strings. */
@Serializable
data class FileSystemResult(
    val parent: String? = null,
    val directories: List<FileSystemModel>? = null,
    val files: List<FileSystemModel>? = null,
)

/** One entry of a filesystem listing; `lastModified` stays a passthrough string. */
@Serializable
data class FileSystemModel(
    val type: FileSystemEntityType? = null,
    val name: String? = null,
    val path: String? = null,
    val extension: String? = null,
    val size: Long = 0,
    val lastModified: String? = null,
)
