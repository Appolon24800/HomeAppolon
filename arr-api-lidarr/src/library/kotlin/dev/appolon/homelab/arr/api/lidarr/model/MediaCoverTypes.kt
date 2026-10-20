package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class MediaCoverTypes {
    @SerialName("unknown") UNKNOWN,
    @SerialName("poster") POSTER,
    @SerialName("banner") BANNER,
    @SerialName("fanart") FANART,
    @SerialName("screenshot") SCREENSHOT,
    @SerialName("headshot") HEADSHOT,
    @SerialName("cover") COVER,
    @SerialName("disc") DISC,
    @SerialName("logo") LOGO,
    @SerialName("clearlogo") CLEARLOGO,
}
