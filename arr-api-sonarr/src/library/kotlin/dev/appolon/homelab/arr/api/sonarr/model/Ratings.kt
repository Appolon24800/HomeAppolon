package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class Ratings(
    val votes: Int = 0,
    val value: Double = 0.0
)
