package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr QueueStatus. Dates remain unparsed strings. */
@Serializable
enum class QueueStatus {
    @SerialName("unknown") UNKNOWN,
    @SerialName("queued") QUEUED,
    @SerialName("paused") PAUSED,
    @SerialName("downloading") DOWNLOADING,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED,
    @SerialName("warning") WARNING,
    @SerialName("delay") DELAY,
    @SerialName("downloadClientUnavailable") DOWNLOAD_CLIENT_UNAVAILABLE,
    @SerialName("fallback") FALLBACK,
    ;

    override fun toString(): String = when (this) {
        UNKNOWN -> "unknown"
        QUEUED -> "queued"
        PAUSED -> "paused"
        DOWNLOADING -> "downloading"
        COMPLETED -> "completed"
        FAILED -> "failed"
        WARNING -> "warning"
        DELAY -> "delay"
        DOWNLOAD_CLIENT_UNAVAILABLE -> "downloadClientUnavailable"
        FALLBACK -> "fallback"
    }
}
