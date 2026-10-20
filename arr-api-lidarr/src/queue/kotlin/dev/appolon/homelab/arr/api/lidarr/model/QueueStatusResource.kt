package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class QueueStatusResource(
    val id: Int = 0,
    val totalCount: Int = 0,
    val count: Int = 0,
    val unknownCount: Int = 0,
    val errors: Boolean = false,
    val warnings: Boolean = false,
    val unknownErrors: Boolean = false,
    val unknownWarnings: Boolean = false,
)
