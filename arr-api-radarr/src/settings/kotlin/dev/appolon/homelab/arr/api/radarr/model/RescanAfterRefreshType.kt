package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr RescanAfterRefreshType wire enum, constants in spec order. */
@Serializable
enum class RescanAfterRefreshType {
    @SerialName("always") ALWAYS,
    @SerialName("afterManual") AFTER_MANUAL,
    @SerialName("never") NEVER,
    ;

    override fun toString(): String = when (this) {
        ALWAYS -> "always"
        AFTER_MANUAL -> "afterManual"
        NEVER -> "never"
    }
}
