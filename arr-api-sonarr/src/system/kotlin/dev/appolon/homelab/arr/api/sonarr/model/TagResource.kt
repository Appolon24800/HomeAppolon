package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr TagResource wire contract. */
@Serializable
data class TagResource(
    val id: Int = 0,
    val label: String? = null
)
