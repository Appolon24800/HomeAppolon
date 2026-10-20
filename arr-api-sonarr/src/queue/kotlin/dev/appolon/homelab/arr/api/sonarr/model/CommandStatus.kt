package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr CommandStatus schema. */
@Serializable
enum class CommandStatus(val wireValue: String) {
    @SerialName("queued")
    QUEUED("queued"),
    @SerialName("started")
    STARTED("started"),
    @SerialName("completed")
    COMPLETED("completed"),
    @SerialName("failed")
    FAILED("failed"),
    @SerialName("aborted")
    ABORTED("aborted"),
    @SerialName("cancelled")
    CANCELLED("cancelled"),
    @SerialName("orphaned")
    ORPHANED("orphaned");

    override fun toString(): String = wireValue
}
