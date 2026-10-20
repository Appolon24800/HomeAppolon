package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr TagDetailsResource wire contract: where one tag is referenced. */
@Serializable
data class TagDetailsResource(
    val id: Int = 0,
    val label: String? = null,
    val delayProfileIds: List<Int>? = null,
    val importListIds: List<Int>? = null,
    val notificationIds: List<Int>? = null,
    val restrictionIds: List<Int>? = null,
    val excludedReleaseProfileIds: List<Int>? = null,
    val indexerIds: List<Int>? = null,
    val downloadClientIds: List<Int>? = null,
    val autoTagIds: List<Int>? = null,
    val seriesIds: List<Int>? = null
)
