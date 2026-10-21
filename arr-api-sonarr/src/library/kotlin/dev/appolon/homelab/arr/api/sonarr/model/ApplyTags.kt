package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class ApplyTags {
    @SerialName("add") ADD,
    @SerialName("remove") REMOVE,
    @SerialName("replace") REPLACE
}
