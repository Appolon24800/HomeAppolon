package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr FileDateType wire enum, constants in spec order. */
@Serializable
enum class FileDateType {
    @SerialName("none") NONE,
    @SerialName("cinemas") CINEMAS,
    @SerialName("release") RELEASE,
    ;

    override fun toString(): String = when (this) {
        NONE -> "none"
        CINEMAS -> "cinemas"
        RELEASE -> "release"
    }
}
