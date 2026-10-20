package dev.appolon.homelab.arr.feature.prowlarr.stats.data

import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.core.ArrTime
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Window the stats screen aggregates over. The indexerstats endpoint takes an
 * explicit start and end date, so each choice maps to a lookback duration from
 * now; months count as 30 days and years as 365 so windows stay fixed-length
 * and testable.
 */
enum class StatsPeriod(val wireName: String, private val lookback: Duration) {
    DAY("day", Duration.ofDays(1)),
    WEEK("week", Duration.ofDays(7)),
    MONTH("month", Duration.ofDays(30)),
    YEAR("year", Duration.ofDays(365));

    /** Inclusive lower bound of the window containing [now]. */
    fun startInstant(now: Instant): Instant = now.minus(lookback)

    /** Wire-format query pair handed to the indexerstats endpoint. */
    fun window(now: Instant): Pair<String, String> =
        ArrTime.formatWire(startInstant(now)) to ArrTime.formatWire(now)
}

/**
 * Cache envelope for one fetched [IndexerStatsResource]. The store keys rows
 * by entity id, so the period rides inside the row and each period caches
 * independently under its own id.
 */
@Serializable
data class CachedStats(
    val period: String,
    val resource: IndexerStatsResource,
)
