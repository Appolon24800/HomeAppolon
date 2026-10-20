package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr ProviderMessageType wire enum, constants in spec order. */
@Serializable
enum class ProviderMessageType {
    @SerialName("info") INFO,
    @SerialName("warning") WARNING,
    @SerialName("error") ERROR,
    ;

    override fun toString(): String = when (this) {
        INFO -> "info"
        WARNING -> "warning"
        ERROR -> "error"
    }
}
