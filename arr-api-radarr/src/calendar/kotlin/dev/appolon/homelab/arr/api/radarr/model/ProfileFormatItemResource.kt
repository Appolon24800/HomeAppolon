package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** One custom format's score contribution inside a quality profile. */
@Serializable
data class ProfileFormatItemResource(
    val id: Int = 0,
    val format: Int = 0,
    val name: String? = null,
    val score: Int = 0,
)
