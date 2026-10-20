package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr TrackedDownloadState schema. */
@Serializable
enum class TrackedDownloadState(val wireValue: String) {
    @SerialName("downloading")
    DOWNLOADING("downloading"),
    @SerialName("importBlocked")
    IMPORT_BLOCKED("importBlocked"),
    @SerialName("importPending")
    IMPORT_PENDING("importPending"),
    @SerialName("importing")
    IMPORTING("importing"),
    @SerialName("imported")
    IMPORTED("imported"),
    @SerialName("failedPending")
    FAILED_PENDING("failedPending"),
    @SerialName("failed")
    FAILED("failed"),
    @SerialName("ignored")
    IGNORED("ignored");

    override fun toString(): String = wireValue
}
