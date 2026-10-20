package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Technical media summary for a movie file. All string fields are free-form server text. */
@Serializable
data class MediaInfoResource(
    val id: Int = 0,
    val audioBitrate: Long = 0L,
    val audioChannels: Double = 0.0,
    val audioCodec: String? = null,
    val audioLanguages: String? = null,
    val audioStreamCount: Int = 0,
    val videoBitDepth: Int = 0,
    val videoBitrate: Long = 0L,
    val videoCodec: String? = null,
    val videoFps: Double = 0.0,
    val videoDynamicRange: String? = null,
    val videoDynamicRangeType: String? = null,
    val resolution: String? = null,
    val runTime: String? = null,
    val scanType: String? = null,
    val subtitles: String? = null,
)
