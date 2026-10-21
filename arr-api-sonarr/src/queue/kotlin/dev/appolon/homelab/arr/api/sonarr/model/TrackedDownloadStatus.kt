package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr TrackedDownloadStatus schema. */
@Serializable
enum class TrackedDownloadStatus(val wireValue: String) {
    @SerialName("ok")
    OK("ok"),
    @SerialName("warning")
    WARNING("warning"),
    @SerialName("error")
    ERROR("error");

    override fun toString(): String = wireValue
}
