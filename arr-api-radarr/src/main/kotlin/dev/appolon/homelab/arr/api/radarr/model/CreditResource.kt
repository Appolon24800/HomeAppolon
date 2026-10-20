package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A cast or crew credit attached to a movie's metadata. */
@Serializable
data class CreditResource(
    val id: Int = 0,
    val personName: String? = null,
    val creditTmdbId: String? = null,
    val personTmdbId: Int = 0,
    val movieMetadataId: Int = 0,
    val images: List<MediaCover>? = null,
    val department: String? = null,
    val job: String? = null,
    val character: String? = null,
    val order: Int = 0,
    val type: CreditType? = null,
)

@Serializable
enum class CreditType {
    @SerialName("cast") CAST,
    @SerialName("crew") CREW,
}
