package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr ColonReplacementFormat wire enum, constants in spec order. */
@Serializable
enum class ColonReplacementFormat {
    @SerialName("delete") DELETE,
    @SerialName("dash") DASH,
    @SerialName("spaceDash") SPACE_DASH,
    @SerialName("spaceDashSpace") SPACE_DASH_SPACE,
    @SerialName("smart") SMART,
    ;

    override fun toString(): String = when (this) {
        DELETE -> "delete"
        DASH -> "dash"
        SPACE_DASH -> "spaceDash"
        SPACE_DASH_SPACE -> "spaceDashSpace"
        SMART -> "smart"
    }
}
