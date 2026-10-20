package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class QualitySource {
    @SerialName("unknown") UNKNOWN,
    @SerialName("television") TELEVISION,
    @SerialName("televisionRaw") TELEVISIONRAW,
    @SerialName("web") WEB,
    @SerialName("webRip") WEBRIP,
    @SerialName("dvd") DVD,
    @SerialName("bluray") BLURAY,
    @SerialName("blurayRaw") BLURAYRAW
}
