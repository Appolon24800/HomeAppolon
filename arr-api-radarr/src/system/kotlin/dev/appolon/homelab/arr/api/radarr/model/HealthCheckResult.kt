package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr HealthCheckResult wire enum, constants in spec order. */
@Serializable
enum class HealthCheckResult {
    @SerialName("ok") OK,
    @SerialName("notice") NOTICE,
    @SerialName("warning") WARNING,
    @SerialName("error") ERROR,
    ;

    override fun toString(): String = when (this) {
        OK -> "ok"
        NOTICE -> "notice"
        WARNING -> "warning"
        ERROR -> "error"
    }
}
