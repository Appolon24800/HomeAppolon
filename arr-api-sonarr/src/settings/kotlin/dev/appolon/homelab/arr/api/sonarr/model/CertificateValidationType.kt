package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr CertificateValidationType schema. */
@Serializable
enum class CertificateValidationType(val wireValue: String) {
    @SerialName("enabled")
    ENABLED("enabled"),

    @SerialName("disabledForLocalAddresses")
    DISABLED_FOR_LOCAL_ADDRESSES("disabledForLocalAddresses"),

    @SerialName("disabled")
    DISABLED("disabled");

    override fun toString(): String = wireValue
}
