package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr RootFolderResource wire contract. Sizes are bytes. */
@Serializable
data class RootFolderResource(
    val id: Int = 0,
    val path: String? = null,
    val accessible: Boolean = false,
    val freeSpace: Long = 0,
    val totalSpace: Long = 0,
    val unmappedFolders: List<UnmappedFolder>? = null
)
