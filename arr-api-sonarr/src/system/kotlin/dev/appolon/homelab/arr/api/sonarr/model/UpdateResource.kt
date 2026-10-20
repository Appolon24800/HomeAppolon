package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr UpdateResource wire contract. Dates remain unparsed strings. */
@Serializable
data class UpdateResource(
    val id: Int = 0,
    val version: String? = null,
    val branch: String? = null,
    val releaseDate: String? = null,
    val fileName: String? = null,
    val url: String? = null,
    val installed: Boolean = false,
    val installedOn: String? = null,
    val installable: Boolean = false,
    val latest: Boolean = false,
    val changes: UpdateChanges? = null,
    val hash: String? = null
)
