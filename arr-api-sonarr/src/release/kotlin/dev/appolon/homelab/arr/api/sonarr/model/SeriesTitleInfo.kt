package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr SeriesTitleInfo wire contract. Dates remain unparsed strings. */
@Serializable
data class SeriesTitleInfo(
    val title: String? = null,
    val titleWithoutYear: String? = null,
    val year: Int = 0,
    val allTitles: List<String>? = null
)
