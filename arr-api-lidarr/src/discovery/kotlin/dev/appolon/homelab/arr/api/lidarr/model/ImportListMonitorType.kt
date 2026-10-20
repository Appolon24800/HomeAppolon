package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class ImportListMonitorType {
    @SerialName("none") NONE,
    @SerialName("specificAlbum") SPECIFIC_ALBUM,
    @SerialName("entireArtist") ENTIRE_ARTIST,
}
