package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr Member wire resource. Dates remain unparsed strings. */
@Serializable
data class Member(
    val name: String? = null,
    val instrument: String? = null,
    val images: List<MediaCover>? = null,
)
