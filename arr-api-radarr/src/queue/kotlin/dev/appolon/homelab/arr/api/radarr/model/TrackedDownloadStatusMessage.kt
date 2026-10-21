package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr TrackedDownloadStatusMessage. Dates remain unparsed strings. */
@Serializable
data class TrackedDownloadStatusMessage(
    val title: String? = null,
    val messages: List<String>? = null,
)
