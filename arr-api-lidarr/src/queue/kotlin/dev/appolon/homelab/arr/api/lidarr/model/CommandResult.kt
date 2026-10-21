package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CommandResult {
    @SerialName("unknown") UNKNOWN,
    @SerialName("successful") SUCCESSFUL,
    @SerialName("unsuccessful") UNSUCCESSFUL,
}
