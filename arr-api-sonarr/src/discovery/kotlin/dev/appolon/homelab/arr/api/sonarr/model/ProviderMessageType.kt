package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr ProviderMessageType schema. */
@Serializable
enum class ProviderMessageType {
    @SerialName("info") INFO,
    @SerialName("warning") WARNING,
    @SerialName("error") ERROR
}
