package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

@Serializable
data class RenameMovieResource(
    val id: Int = 0,
    val movieId: Int = 0,
    val movieFileId: Int = 0,
    val existingPath: String? = null,
    val newPath: String? = null,
)
