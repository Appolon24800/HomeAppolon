package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr IndexerFlagResource wire resource for one release flag. */
@Serializable
data class IndexerFlagResource(
    val id: Int = 0,
    val name: String? = null,
    val nameLower: String? = null,
)
