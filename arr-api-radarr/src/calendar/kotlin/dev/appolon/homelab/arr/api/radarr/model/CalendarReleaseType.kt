package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for the calendar feed's `releaseTypes` filter. */
@Serializable
enum class CalendarReleaseType {
    @SerialName("cinemaRelease") CINEMA_RELEASE,
    @SerialName("digitalRelease") DIGITAL_RELEASE,
    @SerialName("physicalRelease") PHYSICAL_RELEASE,
    ;

    override fun toString(): String = when (this) {
        CINEMA_RELEASE -> "cinemaRelease"
        DIGITAL_RELEASE -> "digitalRelease"
        PHYSICAL_RELEASE -> "physicalRelease"
    }
}
