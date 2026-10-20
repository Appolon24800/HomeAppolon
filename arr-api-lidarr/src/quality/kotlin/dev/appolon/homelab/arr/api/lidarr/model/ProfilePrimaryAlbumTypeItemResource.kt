package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Primary album type allowance inside a metadata profile. */
@Serializable
data class ProfilePrimaryAlbumTypeItemResource(
    val id: Int = 0,
    val albumType: PrimaryAlbumType? = null,
    val allowed: Boolean = false,
)
