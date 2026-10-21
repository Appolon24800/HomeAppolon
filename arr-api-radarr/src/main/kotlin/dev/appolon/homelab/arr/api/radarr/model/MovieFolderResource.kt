package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Response of the movie folder endpoint: the folder Radarr would place a movie in. */
@Serializable
data class MovieFolderResource(
    val folder: String? = null,
)
