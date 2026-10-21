package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Folder found under a root folder that no series maps to yet. */
@Serializable
data class UnmappedFolder(
    val name: String? = null,
    val path: String? = null,
    val relativePath: String? = null
)
