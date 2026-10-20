package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr UpdateResource. Dates remain unparsed strings. */
@Serializable
data class UpdateResource(
    val id: Int = 0,
    val version: String? = null,
    val branch: String? = null,
    val releaseDate: String = "",
    val fileName: String? = null,
    val url: String? = null,
    val installed: Boolean = false,
    val installedOn: String? = null,
    val installable: Boolean = false,
    val latest: Boolean = false,
    val changes: UpdateChanges? = null,
    val hash: String? = null,
)

/** Changelog attached to an update package. `new` is a Kotlin keyword, hence the rename. */
@Serializable
data class UpdateChanges(
    @SerialName("new") val newFeatures: List<String>? = null,
    @SerialName("fixed") val fixed: List<String>? = null,
)
