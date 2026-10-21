package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr MovieRuntimeFormatType wire enum, constants in spec order. */
@Serializable
enum class MovieRuntimeFormatType {
    @SerialName("hoursMinutes") HOURS_MINUTES,
    @SerialName("minutes") MINUTES,
    ;

    override fun toString(): String = when (this) {
        HOURS_MINUTES -> "hoursMinutes"
        MINUTES -> "minutes"
    }
}
