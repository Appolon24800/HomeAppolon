package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ImportListExclusionResource wire contract. */
@Serializable
data class ImportListExclusionResource(
    val id: Int = 0,
    val tvdbId: Int = 0,
    val title: String? = null
)
