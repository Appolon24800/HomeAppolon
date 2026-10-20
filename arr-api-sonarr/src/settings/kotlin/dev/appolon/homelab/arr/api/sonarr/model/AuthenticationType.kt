package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr AuthenticationType schema. */
@Serializable
enum class AuthenticationType(val wireValue: String) {
    @SerialName("none")
    NONE("none"),

    @SerialName("basic")
    BASIC("basic"),

    @SerialName("forms")
    FORMS("forms"),

    @SerialName("external")
    EXTERNAL("external"),

    @SerialName("oidc")
    OIDC("oidc");

    override fun toString(): String = wireValue
}
