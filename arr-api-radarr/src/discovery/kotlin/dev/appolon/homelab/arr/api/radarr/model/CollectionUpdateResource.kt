package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr CollectionUpdateResource.
 * Dates remain unparsed strings. */
@Serializable
data class CollectionUpdateResource(
    val collectionIds: List<Int>? = null,
    val monitored: Boolean? = null,
    val monitorMovies: Boolean? = null,
    val searchOnAdd: Boolean? = null,
    val qualityProfileId: Int? = null,
    val rootFolderPath: String? = null,
    val minimumAvailability: MovieStatusType? = null,
)
