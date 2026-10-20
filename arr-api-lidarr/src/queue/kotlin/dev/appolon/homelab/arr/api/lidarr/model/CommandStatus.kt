package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CommandStatus {
    @SerialName("queued") QUEUED,
    @SerialName("started") STARTED,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED,
    @SerialName("aborted") ABORTED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("orphaned") ORPHANED,
}
