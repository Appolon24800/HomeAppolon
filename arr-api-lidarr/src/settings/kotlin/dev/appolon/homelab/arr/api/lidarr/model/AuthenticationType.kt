package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class AuthenticationType {
    @SerialName("none") NONE,
    @SerialName("basic") BASIC,
    @SerialName("forms") FORMS,
    @SerialName("external") EXTERNAL;
}
