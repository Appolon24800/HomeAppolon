package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr CommandPriority schema. */
@Serializable
enum class CommandPriority(val wireValue: String) {
    @SerialName("normal")
    NORMAL("normal"),
    @SerialName("high")
    HIGH("high"),
    @SerialName("low")
    LOW("low");

    override fun toString(): String = wireValue
}
