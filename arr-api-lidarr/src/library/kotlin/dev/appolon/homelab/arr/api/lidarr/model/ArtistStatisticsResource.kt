package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr ArtistStatisticsResource wire resource. Dates remain unparsed strings. */
@Serializable
data class ArtistStatisticsResource(
    val albumCount: Int = 0,
    val trackFileCount: Int = 0,
    val trackCount: Int = 0,
    val totalTrackCount: Int = 0,
    val sizeOnDisk: Long = 0L,
    val percentOfTracks: Double = 0.0,
)
