package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class SeriesStatusType {
    @SerialName("continuing") CONTINUING,
    @SerialName("ended") ENDED,
    @SerialName("upcoming") UPCOMING,
    @SerialName("deleted") DELETED
}
