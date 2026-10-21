package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class ImportListType {
    @SerialName("program") PROGRAM,
    @SerialName("spotify") SPOTIFY,
    @SerialName("lastFm") LAST_FM,
    @SerialName("other") OTHER,
    @SerialName("advanced") ADVANCED,
}
