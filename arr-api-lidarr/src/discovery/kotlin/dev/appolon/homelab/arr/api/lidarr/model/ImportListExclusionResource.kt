package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr ImportListExclusionResource wire resource. */
@Serializable
data class ImportListExclusionResource(
    val id: Int = 0,
    val foreignId: String? = null,
    val artistName: String? = null,
)
