package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Per-indexer rollup inside [IndexerStatsResource]: response times plus query,
 * grab and failure counters.
 */
@Serializable
data class IndexerStatistics(
    val indexerId: Int = 0,
    val indexerName: String? = null,
    val averageResponseTime: Int = 0,
    val averageGrabResponseTime: Int = 0,
    val numberOfQueries: Int = 0,
    val numberOfGrabs: Int = 0,
    val numberOfRssQueries: Int = 0,
    val numberOfAuthQueries: Int = 0,
    val numberOfFailedQueries: Int = 0,
    val numberOfFailedGrabs: Int = 0,
    val numberOfFailedRssQueries: Int = 0,
    val numberOfFailedAuthQueries: Int = 0,
)
