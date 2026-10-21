package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CommandPriority {
    @SerialName("normal") NORMAL,
    @SerialName("high") HIGH,
    @SerialName("low") LOW,
}
