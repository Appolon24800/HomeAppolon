package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr AuthenticationType wire enum, constants in spec order. */
@Serializable
enum class AuthenticationType {
    @SerialName("none") NONE,
    @SerialName("basic") BASIC,
    @SerialName("forms") FORMS,
    @SerialName("external") EXTERNAL,
    @SerialName("oidc") OIDC,
    ;

    override fun toString(): String = when (this) {
        NONE -> "none"
        BASIC -> "basic"
        FORMS -> "forms"
        EXTERNAL -> "external"
        OIDC -> "oidc"
    }
}
