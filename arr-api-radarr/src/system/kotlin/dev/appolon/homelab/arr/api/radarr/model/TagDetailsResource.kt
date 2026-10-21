package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr TagDetailsResource. Dates remain unparsed strings.
 * Each `*Ids` list names the entities currently carrying the tag. */
@Serializable
data class TagDetailsResource(
    val id: Int = 0,
    val label: String? = null,
    val delayProfileIds: List<Int>? = null,
    val importListIds: List<Int>? = null,
    val notificationIds: List<Int>? = null,
    val releaseProfileIds: List<Int>? = null,
    val indexerIds: List<Int>? = null,
    val downloadClientIds: List<Int>? = null,
    val autoTagIds: List<Int>? = null,
    val movieIds: List<Int>? = null,
)
