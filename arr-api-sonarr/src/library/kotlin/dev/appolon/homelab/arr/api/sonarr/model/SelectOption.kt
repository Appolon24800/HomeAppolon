package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class SelectOption(
    val value: Int = 0,
    val name: String? = null,
    val order: Int = 0,
    val hint: String? = null
)
