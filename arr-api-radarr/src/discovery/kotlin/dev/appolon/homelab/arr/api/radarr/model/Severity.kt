package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr Severity wire enum, constants in spec order. */
@Serializable
enum class Severity {
    @SerialName("error") ERROR,
    @SerialName("warning") WARNING,
    @SerialName("info") INFO,
    ;

    override fun toString(): String = when (this) {
        ERROR -> "error"
        WARNING -> "warning"
        INFO -> "info"
    }
}
