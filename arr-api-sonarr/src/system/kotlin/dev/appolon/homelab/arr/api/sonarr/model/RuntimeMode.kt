package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr RuntimeMode schema. */
@Serializable
enum class RuntimeMode(val wireValue: String) {
    @SerialName("console")
    CONSOLE("console"),

    @SerialName("service")
    SERVICE("service"),

    @SerialName("tray")
    TRAY("tray");

    override fun toString(): String = wireValue
}
