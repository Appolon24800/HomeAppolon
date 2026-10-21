package dev.appolon.homelab.arr.feature.prowlarr.stats.data

import dev.appolon.homelab.arr.api.prowlarr.model.HostStatistics
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatistics
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.api.prowlarr.model.UserAgentStatistics
import kotlin.math.roundToInt

/**
 * Host-level rollup of one stats window: every counter the totals row shows.
 * [grabRatioPercent] is grabs over queries; [averageResponseTimeMs] weights
 * each indexer's average by its query count, falling back to the plain mean
 * when nothing was queried yet.
 */
data class StatsTotals(
    val queries: Int,
    val grabs: Int,
    val failedQueries: Int,
    val failedGrabs: Int,
    val grabRatioPercent: Double,
    val averageResponseTimeMs: Int,
)

/**
 * Pure derivation from [IndexerStatsResource]; no Android or IO types so the
 * screen's numbers are unit-testable. Sorting is deterministic: indexers by
 * queries descending then name, hosts and user agents by queries descending
 * then label, so equal counters keep a stable order.
 */
object StatsPresentation {

    fun totals(indexers: List<IndexerStatistics>): StatsTotals {
        val queries = indexers.sumOf { it.numberOfQueries }
        val grabs = indexers.sumOf { it.numberOfGrabs }
        val weighted = indexers.sumOf { it.averageResponseTime.toLong() * it.numberOfQueries }
        val average = if (queries > 0) {
            (weighted / queries).toInt()
        } else {
            indexers.takeIf { it.isNotEmpty() }
                ?.let { list -> list.sumOf { it.averageResponseTime } / list.size }
                ?: 0
        }
        return StatsTotals(
            queries = queries,
            grabs = grabs,
            failedQueries = indexers.sumOf { it.numberOfFailedQueries },
            failedGrabs = indexers.sumOf { it.numberOfFailedGrabs },
            grabRatioPercent = ratioPercent(grabs, queries),
            averageResponseTimeMs = average,
        )
    }

    /** Grabs per hundred queries as a double; zero when nothing was queried. */
    fun ratioPercent(grabs: Int, queries: Int): Double =
        if (queries <= 0) 0.0 else grabs * 100.0 / queries

    fun sortedByQueries(indexers: List<IndexerStatistics>): List<IndexerStatistics> =
        indexers.sortedWith(
            compareByDescending<IndexerStatistics> { it.numberOfQueries }
                .thenByDescending { it.numberOfGrabs }
                .thenBy { it.indexerName.orEmpty() },
        )

    fun hosts(resource: IndexerStatsResource): List<HostStatistics> =
        resource.hosts.orEmpty().sortedWith(
            compareByDescending<HostStatistics> { it.numberOfQueries }
                .thenByDescending { it.numberOfGrabs }
                .thenBy { it.host.orEmpty() },
        )

    fun userAgents(resource: IndexerStatsResource): List<UserAgentStatistics> =
        resource.userAgents.orEmpty().sortedWith(
            compareByDescending<UserAgentStatistics> { it.numberOfQueries }
                .thenByDescending { it.numberOfGrabs }
                .thenBy { it.userAgent.orEmpty() },
        )

    /** True when any section carries a counter above zero. */
    fun hasData(resource: IndexerStatsResource): Boolean =
        resource.indexers.orEmpty().any { it.numberOfQueries > 0 || it.numberOfGrabs > 0 } ||
            resource.hosts.orEmpty().any { it.numberOfQueries > 0 || it.numberOfGrabs > 0 } ||
            resource.userAgents.orEmpty()
                .any { it.numberOfQueries > 0 || it.numberOfGrabs > 0 }

    /** Share of one value within [total] as whole percent, 0 when total is 0. */
    fun sharePercent(value: Int, total: Int): Int =
        if (total <= 0) 0 else (value * 100.0 / total).roundToInt()
}
