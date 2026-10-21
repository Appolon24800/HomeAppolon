package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr CommandResult schema. */
@Serializable
enum class CommandResult(val wireValue: String) {
    @SerialName("unknown")
    UNKNOWN("unknown"),
    @SerialName("successful")
    SUCCESSFUL("successful"),
    @SerialName("unsuccessful")
    UNSUCCESSFUL("unsuccessful"),
    @SerialName("indeterminate")
    INDETERMINATE("indeterminate");

    override fun toString(): String = wireValue
}
