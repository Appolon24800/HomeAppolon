package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr CommandTrigger schema. */
@Serializable
enum class CommandTrigger(val wireValue: String) {
    @SerialName("unspecified")
    UNSPECIFIED("unspecified"),
    @SerialName("manual")
    MANUAL("manual"),
    @SerialName("scheduled")
    SCHEDULED("scheduled");

    override fun toString(): String = wireValue
}
