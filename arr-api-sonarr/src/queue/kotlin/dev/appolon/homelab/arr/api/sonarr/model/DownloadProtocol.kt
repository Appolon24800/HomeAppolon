package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr DownloadProtocol schema. */
@Serializable
enum class DownloadProtocol(val wireValue: String) {
    @SerialName("unknown")
    UNKNOWN("unknown"),
    @SerialName("usenet")
    USENET("usenet"),
    @SerialName("torrent")
    TORRENT("torrent");

    override fun toString(): String = wireValue
}
