package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Secondary album type allowance inside a metadata profile. */
@Serializable
data class ProfileSecondaryAlbumTypeItemResource(
    val id: Int = 0,
    val albumType: SecondaryAlbumType? = null,
    val allowed: Boolean = false,
)
