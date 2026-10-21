package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr SecondaryAlbumType wire resource. The spec does not enumerate values. */
@Serializable
data class SecondaryAlbumType(
    val id: Int = 0,
    val name: String? = null,
)
