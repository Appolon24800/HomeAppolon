package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr CommandTrigger. Dates remain unparsed strings. */
@Serializable
enum class CommandTrigger {
    @SerialName("unspecified") UNSPECIFIED,
    @SerialName("manual") MANUAL,
    @SerialName("scheduled") SCHEDULED,
    ;

    override fun toString(): String = when (this) {
        UNSPECIFIED -> "unspecified"
        MANUAL -> "manual"
        SCHEDULED -> "scheduled"
    }
}
