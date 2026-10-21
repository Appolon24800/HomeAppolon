package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class PrivacyLevel {
    @SerialName("normal") NORMAL,
    @SerialName("password") PASSWORD,
    @SerialName("apiKey") APIKEY,
    @SerialName("userName") USERNAME
}
