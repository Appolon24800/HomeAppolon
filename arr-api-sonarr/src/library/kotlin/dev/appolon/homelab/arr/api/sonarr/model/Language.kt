package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class Language(
    val id: Int = 0,
    val name: String? = null
)
