package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Release flag advertised by an indexer, for example `g` or `nzbindex`. */
@Serializable
data class IndexerFlagResource(
    val id: Int = 0,
    val name: String? = null,
    val nameLower: String? = null
)
