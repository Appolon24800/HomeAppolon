package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr RetagTrackResource wire resource. Dates remain unparsed strings. */
@Serializable
data class RetagTrackResource(
    val id: Int = 0,
    val artistId: Int = 0,
    val albumId: Int = 0,
    val trackNumbers: List<Int>? = null,
    val trackFileId: Int = 0,
    val path: String? = null,
    val changes: List<TagDifference>? = null,
)
