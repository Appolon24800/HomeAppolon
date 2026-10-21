package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr LogFileResource. Dates remain unparsed strings. */
@Serializable
data class LogFileResource(
    val id: Int = 0,
    val filename: String? = null,
    val lastWriteTime: String = "",
    val contentsUrl: String? = null,
    val downloadUrl: String? = null,
)
