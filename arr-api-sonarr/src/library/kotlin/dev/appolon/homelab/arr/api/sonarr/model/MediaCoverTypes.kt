package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class MediaCoverTypes {
    @SerialName("unknown") UNKNOWN,
    @SerialName("poster") POSTER,
    @SerialName("banner") BANNER,
    @SerialName("fanart") FANART,
    @SerialName("screenshot") SCREENSHOT,
    @SerialName("headshot") HEADSHOT,
    @SerialName("clearlogo") CLEARLOGO
}
