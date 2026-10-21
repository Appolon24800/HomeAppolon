package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr ProxyType schema. */
@Serializable
enum class ProxyType(val wireValue: String) {
    @SerialName("http")
    HTTP("http"),

    @SerialName("socks4")
    SOCKS4("socks4"),

    @SerialName("socks5")
    SOCKS5("socks5");

    override fun toString(): String = wireValue
}
