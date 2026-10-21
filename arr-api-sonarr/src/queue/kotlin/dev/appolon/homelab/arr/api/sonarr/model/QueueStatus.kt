package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr QueueStatus schema. */
@Serializable
enum class QueueStatus(val wireValue: String) {
    @SerialName("unknown")
    UNKNOWN("unknown"),
    @SerialName("queued")
    QUEUED("queued"),
    @SerialName("paused")
    PAUSED("paused"),
    @SerialName("downloading")
    DOWNLOADING("downloading"),
    @SerialName("completed")
    COMPLETED("completed"),
    @SerialName("failed")
    FAILED("failed"),
    @SerialName("warning")
    WARNING("warning"),
    @SerialName("delay")
    DELAY("delay"),
    @SerialName("downloadClientUnavailable")
    DOWNLOAD_CLIENT_UNAVAILABLE("downloadClientUnavailable"),
    @SerialName("fallback")
    FALLBACK("fallback");

    override fun toString(): String = wireValue
}
