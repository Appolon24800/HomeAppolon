package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr RootFolderResource. Dates remain unparsed strings. */
@Serializable
data class RootFolderResource(
    val id: Int = 0,
    val path: String? = null,
    val accessible: Boolean = false,
    val freeSpace: Long? = null,
    val unmappedFolders: List<UnmappedFolder>? = null,
)

/** A folder inside a root folder that no movie currently maps to. */
@Serializable
data class UnmappedFolder(
    val name: String? = null,
    val path: String? = null,
    val relativePath: String? = null,
)
