package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An alternate release title for a movie's metadata. */
@Serializable
data class AlternativeTitleResource(
    val id: Int = 0,
    val sourceType: SourceType? = null,
    val movieMetadataId: Int = 0,
    val title: String? = null,
    val cleanTitle: String? = null,
)

@Serializable
enum class SourceType {
    @SerialName("tmdb") TMDB,
    @SerialName("mappings") MAPPINGS,
    @SerialName("user") USER,
    @SerialName("indexer") INDEXER,
}
