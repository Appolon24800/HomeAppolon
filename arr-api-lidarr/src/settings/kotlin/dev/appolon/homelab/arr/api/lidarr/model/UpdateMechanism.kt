package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class UpdateMechanism {
    @SerialName("builtIn") BUILT_IN,
    @SerialName("script") SCRIPT,
    @SerialName("external") EXTERNAL,
    @SerialName("apt") APT,
    @SerialName("docker") DOCKER;

    override fun toString(): String = when (this) {
        BUILT_IN -> "builtIn"
        SCRIPT -> "script"
        EXTERNAL -> "external"
        APT -> "apt"
        DOCKER -> "docker"
    }
}
