package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr IndexerConfigResource.
 * Dates remain unparsed strings. */
@Serializable
data class IndexerConfigResource(
    val id: Int = 0,
    val minimumAge: Int = 0,
    val maximumSize: Int = 0,
    val retention: Int = 0,
    val rssSyncInterval: Int = 0,
    val preferIndexerFlags: Boolean = false,
    val availabilityDelay: Int = 0,
    val allowHardcodedSubs: Boolean = false,
    val whitelistedHardcodedSubs: String? = null,
)
