package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class NewItemMonitorTypes {
    @SerialName("all") ALL,
    @SerialName("none") NONE
}
