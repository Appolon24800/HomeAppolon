package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr IndexerConfigResource wire resource for indexer settings. */
@Serializable
data class IndexerConfigResource(
    val id: Int = 0,
    val minimumAge: Int = 0,
    val maximumSize: Int = 0,
    val retention: Int = 0,
    val rssSyncInterval: Int = 0,
)
