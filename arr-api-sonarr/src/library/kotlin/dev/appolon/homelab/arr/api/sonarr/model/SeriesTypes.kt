package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class SeriesTypes {
    @SerialName("standard") STANDARD,
    @SerialName("daily") DAILY,
    @SerialName("anime") ANIME
}
