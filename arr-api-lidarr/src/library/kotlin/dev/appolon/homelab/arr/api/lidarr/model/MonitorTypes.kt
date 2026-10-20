package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class MonitorTypes {
    @SerialName("all") ALL,
    @SerialName("future") FUTURE,
    @SerialName("missing") MISSING,
    @SerialName("existing") EXISTING,
    @SerialName("latest") LATEST,
    @SerialName("first") FIRST,
    @SerialName("none") NONE,
    @SerialName("unknown") UNKNOWN,
}
