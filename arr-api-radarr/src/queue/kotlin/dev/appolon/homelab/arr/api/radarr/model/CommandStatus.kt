package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr CommandStatus. Dates remain unparsed strings. */
@Serializable
enum class CommandStatus {
    @SerialName("queued") QUEUED,
    @SerialName("started") STARTED,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED,
    @SerialName("aborted") ABORTED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("orphaned") ORPHANED,
    ;

    override fun toString(): String = when (this) {
        QUEUED -> "queued"
        STARTED -> "started"
        COMPLETED -> "completed"
        FAILED -> "failed"
        ABORTED -> "aborted"
        CANCELLED -> "cancelled"
        ORPHANED -> "orphaned"
    }
}
