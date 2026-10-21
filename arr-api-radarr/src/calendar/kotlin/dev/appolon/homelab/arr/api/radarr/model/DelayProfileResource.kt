package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * A delay profile row. Delays are minutes; `order` is the server-assigned
 * position that the reorder endpoint rewrites for every row at once.
 */
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
    val tags: Set<Int>? = null,
)
