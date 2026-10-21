package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr AuthenticationRequiredType schema. */
@Serializable
enum class AuthenticationRequiredType(val wireValue: String) {
    @SerialName("enabled")
    ENABLED("enabled"),

    @SerialName("disabledForLocalAddresses")
    DISABLED_FOR_LOCAL_ADDRESSES("disabledForLocalAddresses"),

    @SerialName("disabledForLocalhost")
    DISABLED_FOR_LOCALHOST("disabledForLocalhost");

    override fun toString(): String = wireValue
}
