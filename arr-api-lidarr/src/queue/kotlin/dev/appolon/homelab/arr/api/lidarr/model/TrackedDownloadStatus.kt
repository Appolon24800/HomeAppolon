package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TrackedDownloadStatus {
    @SerialName("ok") OK,
    @SerialName("warning") WARNING,
    @SerialName("error") ERROR,
}
