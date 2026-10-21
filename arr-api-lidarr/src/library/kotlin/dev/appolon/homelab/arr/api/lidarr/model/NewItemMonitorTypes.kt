package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class NewItemMonitorTypes {
    @SerialName("all") ALL,
    @SerialName("none") NONE,
    @SerialName("new") NEW,
}
