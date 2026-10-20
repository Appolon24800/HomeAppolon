package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr TagResource wire resource. */
@Serializable
data class TagResource(
    val id: Int = 0,
    val label: String? = null,
)
