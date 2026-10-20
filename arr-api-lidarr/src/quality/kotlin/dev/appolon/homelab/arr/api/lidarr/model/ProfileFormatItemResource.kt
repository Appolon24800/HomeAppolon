package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Custom format score entry attached to a quality profile. */
@Serializable
data class ProfileFormatItemResource(
    val id: Int = 0,
    val format: Int = 0,
    val name: String? = null,
    val score: Int = 0,
)
