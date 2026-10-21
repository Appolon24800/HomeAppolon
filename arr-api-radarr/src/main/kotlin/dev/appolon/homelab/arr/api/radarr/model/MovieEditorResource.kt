package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Batch edit payload for the movie editor endpoints. Null fields are left
 * untouched on every listed movie; `applyTags` selects how `tags` merges.
 */
@Serializable
data class MovieEditorResource(
    val movieIds: List<Int>? = null,
    val monitored: Boolean? = null,
    val qualityProfileId: Int? = null,
    val minimumAvailability: MovieStatusType? = null,
    val rootFolderPath: String? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags? = null,
    val moveFiles: Boolean = false,
    val deleteFiles: Boolean = false,
    val addImportExclusion: Boolean = false,
)

@Serializable
enum class ApplyTags {
    @SerialName("add") ADD,
    @SerialName("remove") REMOVE,
    @SerialName("replace") REPLACE,
}
