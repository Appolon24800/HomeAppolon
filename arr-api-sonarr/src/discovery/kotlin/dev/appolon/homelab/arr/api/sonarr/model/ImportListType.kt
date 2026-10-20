package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr ImportListType schema. */
@Serializable
enum class ImportListType {
    @SerialName("program") PROGRAM,
    @SerialName("plex") PLEX,
    @SerialName("trakt") TRAKT,
    @SerialName("simkl") SIMKL,
    @SerialName("other") OTHER,
    @SerialName("advanced") ADVANCED
}
