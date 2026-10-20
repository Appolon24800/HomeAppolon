package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Failure and cooldown state for one indexer. Timestamps stay wire-format
 * strings; the UI parses them with :arr-core ArrTime so list rendering never
 * blocks on date formats.
 */
@Serializable
data class IndexerStatusResource(
    val id: Int = 0,
    val indexerId: Int = 0,
    val disabledTill: String? = null,
    val mostRecentFailure: String? = null,
    val initialFailure: String? = null,
)
