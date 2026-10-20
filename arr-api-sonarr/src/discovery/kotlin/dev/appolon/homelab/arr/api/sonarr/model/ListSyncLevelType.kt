package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr ListSyncLevelType schema. */
@Serializable
enum class ListSyncLevelType {
    @SerialName("disabled") DISABLED,
    @SerialName("logOnly") LOG_ONLY,
    @SerialName("keepAndUnmonitor") KEEP_AND_UNMONITOR,
    @SerialName("keepAndTag") KEEP_AND_TAG
}
