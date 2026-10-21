package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class HealthCheckResult {
    @SerialName("ok") OK,
    @SerialName("notice") NOTICE,
    @SerialName("warning") WARNING,
    @SerialName("error") ERROR;
}
