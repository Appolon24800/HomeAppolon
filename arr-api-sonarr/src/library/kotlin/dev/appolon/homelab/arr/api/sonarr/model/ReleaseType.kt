package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class ReleaseType {
    @SerialName("unknown") UNKNOWN,
    @SerialName("singleEpisode") SINGLEEPISODE,
    @SerialName("multiEpisode") MULTIEPISODE,
    @SerialName("seasonPack") SEASONPACK
}
