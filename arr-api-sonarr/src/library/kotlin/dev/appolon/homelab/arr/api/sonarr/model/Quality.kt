package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class Quality(
    val id: Int = 0,
    val name: String? = null,
    val source: QualitySource = QualitySource.UNKNOWN,
    val resolution: Int = 0
)
