package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Singleton Sonarr indexer settings resource. */
@Serializable
data class IndexerConfigResource(
    val id: Int = 0,
    val minimumAge: Int = 0,
    val retention: Int = 0,
    val maximumSize: Int = 0,
    val rssSyncInterval: Int = 0
)
