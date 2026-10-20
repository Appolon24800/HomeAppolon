package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Root of the indexer statistics screen: per-indexer, per-user-agent and
 * per-host rollups for a chosen date window.
 */
@Serializable
data class IndexerStatsResource(
    val id: Int = 0,
    val indexers: List<IndexerStatistics>? = null,
    val userAgents: List<UserAgentStatistics>? = null,
    val hosts: List<HostStatistics>? = null,
)
