package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr TrackResource wire resource. Dates remain unparsed strings. */
@Serializable
data class TrackResource(
    val id: Int = 0,
    val artistId: Int = 0,
    val foreignTrackId: String? = null,
    val foreignRecordingId: String? = null,
    val trackFileId: Int = 0,
    val albumId: Int = 0,
    val `explicit`: Boolean = false,
    val absoluteTrackNumber: Int = 0,
    val trackNumber: String? = null,
    val title: String? = null,
    val duration: Int = 0,
    val trackFile: TrackFileResource? = null,
    val mediumNumber: Int = 0,
    val hasFile: Boolean = false,
    val artist: ArtistResource? = null,
    val ratings: Ratings? = null,
)
