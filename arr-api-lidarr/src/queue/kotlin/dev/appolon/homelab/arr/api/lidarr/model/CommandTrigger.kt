package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CommandTrigger {
    @SerialName("unspecified") UNSPECIFIED,
    @SerialName("manual") MANUAL,
    @SerialName("scheduled") SCHEDULED,
}
