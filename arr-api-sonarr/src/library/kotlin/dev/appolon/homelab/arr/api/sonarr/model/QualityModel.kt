package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class QualityModel(
    val quality: Quality = Quality(),
    val revision: Revision = Revision()
)
