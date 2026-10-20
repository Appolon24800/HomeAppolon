package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr TrackedDownloadStatus. Dates remain unparsed strings. */
@Serializable
enum class TrackedDownloadStatus {
    @SerialName("ok") OK,
    @SerialName("warning") WARNING,
    @SerialName("error") ERROR,
    ;

    override fun toString(): String = when (this) {
        OK -> "ok"
        WARNING -> "warning"
        ERROR -> "error"
    }
}
