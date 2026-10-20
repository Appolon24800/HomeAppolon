package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Tag-writing mode enumerated by the Lidarr spec. */
@Serializable
enum class WriteAudioTagsType {
    @SerialName("no") NO,
    @SerialName("newFiles") NEW_FILES,
    @SerialName("allFiles") ALL_FILES,
    @SerialName("sync") SYNC;

    override fun toString(): String = when (this) {
        NO -> "no"
        NEW_FILES -> "newFiles"
        ALL_FILES -> "allFiles"
        SYNC -> "sync"
    }
}
