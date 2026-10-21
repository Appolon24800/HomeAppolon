package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single quality as reported inside quality models and movie statistics. */
@Serializable
data class Quality(
    val id: Int = 0,
    val name: String? = null,
    val source: QualitySource? = null,
    val resolution: Int = 0,
    val modifier: Modifier? = null,
)

/** Quality plus its revision, the shape every `quality` field on the wire uses. */
@Serializable
data class QualityModel(
    val quality: Quality? = null,
    val revision: Revision? = null,
)

/** Release revision of a quality, used to distinguish proper/repack downloads. */
@Serializable
data class Revision(
    val version: Int = 0,
    val real: Int = 0,
    val isRepack: Boolean = false,
)

@Serializable
enum class QualitySource {
    @SerialName("unknown") UNKNOWN,
    @SerialName("cam") CAM,
    @SerialName("telesync") TELESYNC,
    @SerialName("telecine") TELECINE,
    @SerialName("workprint") WORKPRINT,
    @SerialName("dvd") DVD,
    @SerialName("tv") TV,
    @SerialName("webdl") WEBDL,
    @SerialName("webrip") WEBRIP,
    @SerialName("bluray") BLURAY,
}

@Serializable
enum class Modifier {
    @SerialName("none") NONE,
    @SerialName("regional") REGIONAL,
    @SerialName("screener") SCREENER,
    @SerialName("rawhd") RAWHD,
    @SerialName("brdisk") BRDISK,
    @SerialName("remux") REMUX,
}
