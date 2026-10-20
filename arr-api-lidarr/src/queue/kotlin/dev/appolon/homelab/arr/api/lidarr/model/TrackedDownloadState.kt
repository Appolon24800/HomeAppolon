package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TrackedDownloadState {
    @SerialName("downloading") DOWNLOADING,
    @SerialName("downloadFailed") DOWNLOAD_FAILED,
    @SerialName("downloadFailedPending") DOWNLOAD_FAILED_PENDING,
    @SerialName("importBlocked") IMPORT_BLOCKED,
    @SerialName("importPending") IMPORT_PENDING,
    @SerialName("importing") IMPORTING,
    @SerialName("importFailed") IMPORT_FAILED,
    @SerialName("imported") IMPORTED,
    @SerialName("ignored") IGNORED,
}
