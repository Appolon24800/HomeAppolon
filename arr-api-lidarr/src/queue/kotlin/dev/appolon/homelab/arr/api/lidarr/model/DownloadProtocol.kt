package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DownloadProtocol {
    @SerialName("unknown") UNKNOWN,
    @SerialName("usenet") USENET,
    @SerialName("torrent") TORRENT;

    override fun toString(): String = name.lowercase()
}
