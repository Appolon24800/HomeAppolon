package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/**
 * Lidarr LogFileResource wire resource. LastWriteTime stays an unparsed
 * date-time string; the UI formats it through :arr-core ArrTime.
 */
@Serializable
data class LogFileResource(
    val id: Int = 0,
    val filename: String? = null,
    val lastWriteTime: String = "",
    val contentsUrl: String? = null,
    val downloadUrl: String? = null,
)
