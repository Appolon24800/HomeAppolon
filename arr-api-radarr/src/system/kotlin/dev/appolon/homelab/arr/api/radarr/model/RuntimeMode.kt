package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr RuntimeMode wire enum, constants in spec order. */
@Serializable
enum class RuntimeMode {
    @SerialName("console") CONSOLE,
    @SerialName("service") SERVICE,
    @SerialName("tray") TRAY,
    ;

    override fun toString(): String = when (this) {
        CONSOLE -> "console"
        SERVICE -> "service"
        TRAY -> "tray"
    }
}
