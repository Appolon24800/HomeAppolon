package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class ProxyType {
    @SerialName("http") HTTP,
    @SerialName("socks4") SOCKS4,
    @SerialName("socks5") SOCKS5;
}
