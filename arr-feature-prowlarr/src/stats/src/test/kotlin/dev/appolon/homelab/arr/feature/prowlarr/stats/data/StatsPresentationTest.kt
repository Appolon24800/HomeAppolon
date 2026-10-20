package dev.appolon.homelab.arr.feature.prowlarr.stats.data

import dev.appolon.homelab.arr.api.prowlarr.model.HostStatistics
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatistics
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.api.prowlarr.model.UserAgentStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsPresentationTest {

    private val indexers = listOf(
        IndexerStatistics(
            indexerId = 1,
            indexerName = "Alpha",
            averageResponseTime = 100,
            numberOfQueries = 80,
            numberOfGrabs = 4,
            numberOfFailedQueries = 2,
        ),
        IndexerStatistics(
            indexerId = 2,
            indexerName = "Beta",
            averageResponseTime = 300,
            numberOfQueries = 20,
            numberOfGrabs = 6,
        ),
    )

    @Test
    fun `totals sum counters and weight response time by queries`() {
        val totals = StatsPresentation.totals(indexers)
        assertEquals(100, totals.queries)
        assertEquals(10, totals.grabs)
        assertEquals(2, totals.failedQueries)
        // (100*80 + 300*20) / 100 = 140
        assertEquals(140, totals.averageResponseTimeMs)
    }

    @Test
    fun `grab ratio is grabs per hundred queries and zero without queries`() {
        assertEquals(10.0, StatsPresentation.ratioPercent(10, 100), 0.001)
        assertEquals(0.0, StatsPresentation.ratioPercent(3, 0), 0.001)
        val totals = StatsPresentation.totals(
            listOf(IndexerStatistics(indexerId = 1, numberOfQueries = 200, numberOfGrabs = 25)),
        )
        assertEquals(12.5, totals.grabRatioPercent, 0.001)
    }

    @Test
    fun `response average falls back to plain mean when nothing was queried`() {
        val totals = StatsPresentation.totals(
            listOf(
                IndexerStatistics(indexerId = 1, averageResponseTime = 100),
                IndexerStatistics(indexerId = 2, averageResponseTime = 200),
            ),
        )
        assertEquals(150, totals.averageResponseTimeMs)
    }

    @Test
    fun `indexers sort by queries descending with name tiebreak`() {
        val sorted = StatsPresentation.sortedByQueries(
            listOf(
                IndexerStatistics(indexerId = 3, indexerName = "B", numberOfQueries = 5),
                IndexerStatistics(indexerId = 1, indexerName = "A", numberOfQueries = 9),
                IndexerStatistics(indexerId = 2, indexerName = "C", numberOfQueries = 5),
            ),
        )
        assertEquals(listOf(1, 3, 2), sorted.map { it.indexerId })
    }

    @Test
    fun `hosts and user agents sort by queries descending`() {
        val resource = IndexerStatsResource(
            hosts = listOf(
                HostStatistics(host = "radarr", numberOfQueries = 5),
                HostStatistics(host = "sonarr", numberOfQueries = 50),
            ),
            userAgents = listOf(
                UserAgentStatistics(userAgent = "AgentB", numberOfQueries = 1),
                UserAgentStatistics(userAgent = "AgentA", numberOfQueries = 2),
            ),
        )
        assertEquals(listOf("sonarr", "radarr"), StatsPresentation.hosts(resource).map { it.host })
        assertEquals(
            listOf("AgentA", "AgentB"),
            StatsPresentation.userAgents(resource).map { it.userAgent },
        )
    }

    @Test
    fun `hasData is false only when every counter is zero`() {
        val empty = IndexerStatsResource(
            indexers = listOf(IndexerStatistics(indexerId = 1)),
            hosts = listOf(HostStatistics(host = "h")),
            userAgents = listOf(UserAgentStatistics(userAgent = "ua")),
        )
        assertFalse(StatsPresentation.hasData(empty))
        assertTrue(StatsPresentation.hasData(IndexerStatsResource(indexers = indexers)))
        assertTrue(
            StatsPresentation.hasData(
                IndexerStatsResource(hosts = listOf(HostStatistics(host = "h", numberOfGrabs = 1))),
            ),
        )
    }

    @Test
    fun `share percent rounds and guards zero totals`() {
        assertEquals(25, StatsPresentation.sharePercent(1, 4))
        assertEquals(33, StatsPresentation.sharePercent(1, 3))
        assertEquals(0, StatsPresentation.sharePercent(1, 0))
    }
}
