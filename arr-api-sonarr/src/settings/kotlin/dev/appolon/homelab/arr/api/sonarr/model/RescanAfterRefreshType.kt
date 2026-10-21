package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr RescanAfterRefreshType schema. */
@Serializable
enum class RescanAfterRefreshType(val wireValue: String) {
    @SerialName("always")
    ALWAYS("always"),

    @SerialName("afterManual")
    AFTER_MANUAL("afterManual"),

    @SerialName("never")
    NEVER("never");

    override fun toString(): String = wireValue
}
