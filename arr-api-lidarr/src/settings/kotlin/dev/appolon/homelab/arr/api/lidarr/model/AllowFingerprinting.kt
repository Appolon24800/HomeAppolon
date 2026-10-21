package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class AllowFingerprinting {
    @SerialName("never") NEVER,
    @SerialName("newFiles") NEW_FILES,
    @SerialName("allFiles") ALL_FILES;

    override fun toString(): String = when (this) {
        NEVER -> "never"
        NEW_FILES -> "newFiles"
        ALL_FILES -> "allFiles"
    }
}
