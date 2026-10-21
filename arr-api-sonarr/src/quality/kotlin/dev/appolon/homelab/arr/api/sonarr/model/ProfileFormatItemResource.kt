package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ProfileFormatItemResource wire contract. Format is a CustomFormat id. */
@Serializable
data class ProfileFormatItemResource(
    val id: Int = 0,
    val format: Int = 0,
    val name: String? = null,
    val score: Int = 0
)
