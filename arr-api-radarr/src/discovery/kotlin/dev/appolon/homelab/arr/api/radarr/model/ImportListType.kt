package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr ImportListType wire enum, constants in spec order. */
@Serializable
enum class ImportListType {
    @SerialName("program") PROGRAM,
    @SerialName("tmdb") TMDB,
    @SerialName("trakt") TRAKT,
    @SerialName("plex") PLEX,
    @SerialName("simkl") SIMKL,
    @SerialName("other") OTHER,
    @SerialName("advanced") ADVANCED,
    ;

    override fun toString(): String = when (this) {
        PROGRAM -> "program"
        TMDB -> "tmdb"
        TRAKT -> "trakt"
        PLEX -> "plex"
        SIMKL -> "simkl"
        OTHER -> "other"
        ADVANCED -> "advanced"
    }
}
