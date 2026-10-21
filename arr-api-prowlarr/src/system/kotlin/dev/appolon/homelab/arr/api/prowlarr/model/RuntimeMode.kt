package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How the server process runs, from the spec's RuntimeMode enum. [toString]
 * returns the wire name because values also ride @Query parameters, while
 * bodies use @SerialName.
 */
@Serializable
enum class RuntimeMode(private val wireName: String) {
    @SerialName("console") CONSOLE("console"),
    @SerialName("service") SERVICE("service"),
    @SerialName("tray") TRAY("tray");

    override fun toString(): String = wireName
}
