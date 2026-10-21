package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr DelayProfileResource wire resource. Dates remain unparsed strings. */
@Serializable
data class DelayProfileResource(
    val id: Int = 0,
    val enableUsenet: Boolean = false,
    val enableTorrent: Boolean = false,
    val preferredProtocol: DownloadProtocol? = null,
    val usenetDelay: Int = 0,
    val torrentDelay: Int = 0,
    val bypassIfHighestQuality: Boolean = false,
    val bypassIfAboveCustomFormatScore: Boolean = false,
    val minimumCustomFormatScore: Int = 0,
    val order: Int = 0,
    val tags: List<Int>? = null,
)
