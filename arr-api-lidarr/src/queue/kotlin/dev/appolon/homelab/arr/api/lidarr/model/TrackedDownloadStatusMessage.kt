package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackedDownloadStatusMessage(
    val title: String? = null,
    val messages: List<String>? = null,
)
