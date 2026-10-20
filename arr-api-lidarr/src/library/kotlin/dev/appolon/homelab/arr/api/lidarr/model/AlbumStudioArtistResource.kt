package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr AlbumStudioArtistResource wire resource. Dates remain unparsed strings. */
@Serializable
data class AlbumStudioArtistResource(
    val id: Int = 0,
    val monitored: Boolean? = null,
    val albums: List<AlbumResource>? = null,
)
