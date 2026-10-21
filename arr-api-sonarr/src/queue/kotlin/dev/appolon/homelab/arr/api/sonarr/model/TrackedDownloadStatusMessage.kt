package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr TrackedDownloadStatusMessage wire contract. Dates remain unparsed strings. */
@Serializable
data class TrackedDownloadStatusMessage(
    val title: String? = null,
    val messages: List<String>? = null
)
