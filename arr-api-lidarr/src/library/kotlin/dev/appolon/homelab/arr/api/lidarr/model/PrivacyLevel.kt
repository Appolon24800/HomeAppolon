package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class PrivacyLevel {
    @SerialName("normal") NORMAL,
    @SerialName("password") PASSWORD,
    @SerialName("apiKey") APIKEY,
    @SerialName("userName") USERNAME,
}
