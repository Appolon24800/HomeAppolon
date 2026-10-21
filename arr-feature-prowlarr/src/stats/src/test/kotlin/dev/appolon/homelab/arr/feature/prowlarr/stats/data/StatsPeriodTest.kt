package dev.appolon.homelab.arr.feature.prowlarr.stats.data

import dev.appolon.homelab.arr.core.ArrTime
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsPeriodTest {

    @Test
    fun `window spans lookback to now in wire format`() {
        val now = Instant.parse("2026-10-20T12:00:00Z")
        StatsPeriod.entries.forEach { period ->
            val (start, end) = period.window(now)
            assertEquals(ArrTime.formatWire(period.startInstant(now)), start)
            assertEquals("2026-10-20T12:00:00Z", end)
            assertEquals(period.startInstant(now), ArrTime.parse(start))
        }
    }

    @Test
    fun `lookbacks are day week month and year`() {
        val now = Instant.parse("2026-10-20T12:00:00Z")
        assertEquals(now.minusSeconds(86_400), StatsPeriod.DAY.startInstant(now))
        assertEquals(now.minusSeconds(7 * 86_400), StatsPeriod.WEEK.startInstant(now))
        assertEquals(now.minusSeconds(30 * 86_400), StatsPeriod.MONTH.startInstant(now))
        assertEquals(now.minusSeconds(365 * 86_400), StatsPeriod.YEAR.startInstant(now))
    }
}
