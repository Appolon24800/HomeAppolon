package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One published server update from `GET /api/v1/update`. Timestamps stay
 * wire-format strings; the UI parses them with :arr-core ArrTime.
 */
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
    val hash: String? = null,
)

/**
 * Changelog attached to an update package. `new` is a Kotlin keyword, hence
 * the property rename with the wire name kept via @SerialName.
 */
@Serializable
data class UpdateChanges(
    @SerialName("new") val newFeatures: List<String>? = null,
    @SerialName("fixed") val fixed: List<String>? = null,
)
