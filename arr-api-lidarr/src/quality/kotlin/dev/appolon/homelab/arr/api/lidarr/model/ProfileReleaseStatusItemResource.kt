package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Release status allowance inside a metadata profile. */
@Serializable
data class ProfileReleaseStatusItemResource(
    val id: Int = 0,
    val releaseStatus: ReleaseStatus? = null,
    val allowed: Boolean = false,
)
