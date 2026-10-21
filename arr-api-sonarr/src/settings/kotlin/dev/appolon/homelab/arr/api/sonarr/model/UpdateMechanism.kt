package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr UpdateMechanism schema. */
@Serializable
enum class UpdateMechanism(val wireValue: String) {
    @SerialName("builtIn")
    BUILT_IN("builtIn"),

    @SerialName("script")
    SCRIPT("script"),

    @SerialName("external")
    EXTERNAL("external"),

    @SerialName("apt")
    APT("apt"),

    @SerialName("docker")
    DOCKER("docker");

    override fun toString(): String = wireValue
}
