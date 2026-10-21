package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class FileDateType {
    @SerialName("none") NONE,
    @SerialName("albumReleaseDate") ALBUM_RELEASE_DATE;

    override fun toString(): String = when (this) {
        NONE -> "none"
        ALBUM_RELEASE_DATE -> "albumReleaseDate"
    }
}
