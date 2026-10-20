package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr LogResource wire resource. Time stays an unparsed date-time string. */
@Serializable
data class LogResource(
    val id: Int = 0,
    val time: String = "",
    val exception: String? = null,
    val exceptionType: String? = null,
    val level: String? = null,
    val logger: String? = null,
    val message: String? = null,
    val method: String? = null,
)
