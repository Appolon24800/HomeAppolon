package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class Revision(
    val version: Int = 0,
    val real: Int = 0,
    val isRepack: Boolean = false
)
