package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class Rejection(
    val reason: String? = null,
    val type: RejectionType = RejectionType.PERMANENT,
)
