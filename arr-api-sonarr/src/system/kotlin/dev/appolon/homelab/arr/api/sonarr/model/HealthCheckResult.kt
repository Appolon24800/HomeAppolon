package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr HealthCheckResult schema. */
@Serializable
enum class HealthCheckResult(val wireValue: String) {
    @SerialName("ok")
    OK("ok"),

    @SerialName("notice")
    NOTICE("notice"),

    @SerialName("warning")
    WARNING("warning"),

    @SerialName("error")
    ERROR("error");

    override fun toString(): String = wireValue
}
