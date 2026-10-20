package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Sonarr ReleaseProfileResource wire contract. The spec types `required` and
 * `ignored` as unconstrained; Sonarr emits either a comma-joined string or an
 * array across versions, so both stay raw JSON.
 */
@Serializable
data class ReleaseProfileResource(
    val id: Int = 0,
    val name: String? = null,
    val enabled: Boolean = false,
    val required: JsonElement? = null,
    val ignored: JsonElement? = null,
    val airDateRestriction: Boolean = false,
    val airDateGracePeriod: Int = 0,
    val allowSeasonPackWithoutAllEpisodesAired: Boolean = false,
    val indexerId: Int = 0,
    val tags: List<Int>? = null,
    val excludedTags: List<Int>? = null
)
