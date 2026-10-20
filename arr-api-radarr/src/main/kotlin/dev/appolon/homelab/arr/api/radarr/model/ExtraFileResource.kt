package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A sidecar file (subtitle, metadata) belonging to a movie file. */
@Serializable
data class ExtraFileResource(
    val id: Int = 0,
    val movieId: Int = 0,
    val movieFileId: Int? = null,
    val relativePath: String? = null,
    val extension: String? = null,
    val languageTags: List<String>? = null,
    val title: String? = null,
    val type: ExtraFileType? = null,
)

@Serializable
enum class ExtraFileType {
    @SerialName("subtitle") SUBTITLE,
    @SerialName("metadata") METADATA,
    @SerialName("other") OTHER,
}
