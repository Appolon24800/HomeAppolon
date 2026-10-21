package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr TrackedDownloadState. Dates remain unparsed strings. */
@Serializable
enum class TrackedDownloadState {
    @SerialName("downloading") DOWNLOADING,
    @SerialName("importBlocked") IMPORT_BLOCKED,
    @SerialName("importPending") IMPORT_PENDING,
    @SerialName("importing") IMPORTING,
    @SerialName("imported") IMPORTED,
    @SerialName("failedPending") FAILED_PENDING,
    @SerialName("failed") FAILED,
    @SerialName("ignored") IGNORED,
    ;

    override fun toString(): String = when (this) {
        DOWNLOADING -> "downloading"
        IMPORT_BLOCKED -> "importBlocked"
        IMPORT_PENDING -> "importPending"
        IMPORTING -> "importing"
        IMPORTED -> "imported"
        FAILED_PENDING -> "failedPending"
        FAILED -> "failed"
        IGNORED -> "ignored"
    }
}
