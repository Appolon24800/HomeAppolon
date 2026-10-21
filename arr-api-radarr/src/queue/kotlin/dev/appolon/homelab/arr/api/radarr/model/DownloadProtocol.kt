package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr DownloadProtocol. Dates remain unparsed strings. */
@Serializable
enum class DownloadProtocol {
    @SerialName("unknown") UNKNOWN,
    @SerialName("usenet") USENET,
    @SerialName("torrent") TORRENT,
    ;

    override fun toString(): String = when (this) {
        UNKNOWN -> "unknown"
        USENET -> "usenet"
        TORRENT -> "torrent"
    }
}
