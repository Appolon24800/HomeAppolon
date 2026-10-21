package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr TagExistingType schema. */
@Serializable
enum class TagExistingType {
    @SerialName("none") NONE,
    @SerialName("add") ADD,
    @SerialName("sync") SYNC
}
