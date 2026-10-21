package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr AuthenticationRequiredType wire enum, constants in spec order. */
@Serializable
enum class AuthenticationRequiredType {
    @SerialName("enabled") ENABLED,
    @SerialName("disabledForLocalAddresses") DISABLED_FOR_LOCAL_ADDRESSES,
    @SerialName("disabledForLocalhost") DISABLED_FOR_LOCALHOST,
    ;

    override fun toString(): String = when (this) {
        ENABLED -> "enabled"
        DISABLED_FOR_LOCAL_ADDRESSES -> "disabledForLocalAddresses"
        DISABLED_FOR_LOCALHOST -> "disabledForLocalhost"
    }
}
