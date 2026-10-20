package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Image descriptor embedded in movie, credit, and collection payloads.
 * `url` is server-relative when the cover is served by Radarr itself and
 * absolute when it points at a CDN; callers resolve it with the connection
 * layer's prefix-preserving base URL.
 */
@Serializable
data class MediaCover(
    val coverType: MediaCoverTypes? = null,
    val url: String? = null,
    val remoteUrl: String? = null,
)

/**
 * Cover kinds Radarr reports, in spec order. Values newer servers add decode
 * as null (see the module's coercion tests).
 */
@Serializable
enum class MediaCoverTypes {
    @SerialName("unknown") UNKNOWN,
    @SerialName("poster") POSTER,
    @SerialName("banner") BANNER,
    @SerialName("fanart") FANART,
    @SerialName("screenshot") SCREENSHOT,
    @SerialName("headshot") HEADSHOT,
    @SerialName("clearlogo") CLEARLOGO,
}
