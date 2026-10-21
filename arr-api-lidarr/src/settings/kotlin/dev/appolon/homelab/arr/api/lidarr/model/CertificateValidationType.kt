package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class CertificateValidationType {
    @SerialName("enabled") ENABLED,
    @SerialName("disabledForLocalAddresses") DISABLED_FOR_LOCAL_ADDRESSES,
    @SerialName("disabled") DISABLED;

    override fun toString(): String = when (this) {
        ENABLED -> "enabled"
        DISABLED_FOR_LOCAL_ADDRESSES -> "disabledForLocalAddresses"
        DISABLED -> "disabled"
    }
}
