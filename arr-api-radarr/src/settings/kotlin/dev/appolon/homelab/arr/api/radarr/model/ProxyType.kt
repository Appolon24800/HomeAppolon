package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr ProxyType wire enum, constants in spec order. */
@Serializable
enum class ProxyType {
    @SerialName("http") HTTP,
    @SerialName("socks4") SOCKS4,
    @SerialName("socks5") SOCKS5,
    ;

    override fun toString(): String = when (this) {
        HTTP -> "http"
        SOCKS4 -> "socks4"
        SOCKS5 -> "socks5"
    }
}
