package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class RescanAfterRefreshType {
    @SerialName("always") ALWAYS,
    @SerialName("afterManual") AFTER_MANUAL,
    @SerialName("never") NEVER;

    override fun toString(): String = when (this) {
        ALWAYS -> "always"
        AFTER_MANUAL -> "afterManual"
        NEVER -> "never"
    }
}
