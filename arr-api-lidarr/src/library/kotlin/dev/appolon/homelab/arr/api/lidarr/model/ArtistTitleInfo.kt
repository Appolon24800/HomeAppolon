package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr ArtistTitleInfo wire resource. Dates remain unparsed strings. */
@Serializable
data class ArtistTitleInfo(
    val title: String? = null,
    val titleWithoutYear: String? = null,
    val year: Int = 0,
)
