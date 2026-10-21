package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr LogFileResource wire contract. Dates remain unparsed strings. */
@Serializable
data class LogFileResource(
    val id: Int = 0,
    val filename: String? = null,
    val lastWriteTime: String? = null,
    val contentsUrl: String? = null,
    val downloadUrl: String? = null
)
