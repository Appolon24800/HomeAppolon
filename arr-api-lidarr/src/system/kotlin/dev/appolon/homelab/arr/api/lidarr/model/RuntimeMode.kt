package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class RuntimeMode {
    @SerialName("console") CONSOLE,
    @SerialName("service") SERVICE,
    @SerialName("tray") TRAY;
}
