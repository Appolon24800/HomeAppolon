package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/**
 * Lidarr SearchResource wire resource. Each result carries an artist, an album,
 * or both; exactly one is populated per result kind.
 */
@Serializable
data class SearchResource(
    val id: Int = 0,
    val foreignId: String? = null,
    val artist: ArtistResource? = null,
    val album: AlbumResource? = null,
)
