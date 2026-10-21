package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr CommandPriority. Dates remain unparsed strings. */
@Serializable
enum class CommandPriority {
    @SerialName("normal") NORMAL,
    @SerialName("high") HIGH,
    @SerialName("low") LOW,
    ;

    override fun toString(): String = when (this) {
        NORMAL -> "normal"
        HIGH -> "high"
        LOW -> "low"
    }
}
