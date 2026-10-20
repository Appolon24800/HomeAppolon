package dev.appolon.homelab.arr.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeTest {

    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun relativePastSteps() {
        assertEquals("just now", ArrTime.relative(now.minusSeconds(30), now))
        assertEquals("just now", ArrTime.relative(now, now))
        assertEquals("1m ago", ArrTime.relative(now.minusSeconds(60), now))
        assertEquals("59m ago", ArrTime.relative(now.minusSeconds(3_599), now))
        assertEquals("1h ago", ArrTime.relative(now.minusSeconds(3_600), now))
        assertEquals("3h ago", ArrTime.relative(now.minusSeconds(3 * 3_600 + 100), now))
        assertEquals("23h ago", ArrTime.relative(now.minusSeconds(23 * 3_600), now))
        assertEquals("1d ago", ArrTime.relative(now.minusSeconds(24 * 3_600), now))
        assertEquals("6d ago", ArrTime.relative(now.minusSeconds(6 * 86_400), now))
        assertEquals("1w ago", ArrTime.relative(now.minusSeconds(7 * 86_400), now))
        assertEquals("4w ago", ArrTime.relative(now.minusSeconds(28 * 86_400), now))
        assertEquals("1mo ago", ArrTime.relative(now.minusSeconds(30 * 86_400), now))
        assertEquals("1y ago", ArrTime.relative(now.minusSeconds(365 * 86_400), now))
    }

    @Test
    fun relativeFutureSteps() {
        assertEquals("just now", ArrTime.relative(now.plusSeconds(45), now))
        assertEquals("in 5m", ArrTime.relative(now.plusSeconds(5 * 60), now))
        assertEquals("in 3h", ArrTime.relative(now.plusSeconds(3 * 3_600), now))
        assertEquals("in 2d", ArrTime.relative(now.plusSeconds(2 * 86_400), now))
        assertEquals("in 1mo", ArrTime.relative(now.plusSeconds(30 * 86_400), now))
    }

    @Test
    fun relativeMillisOverload() {
        val threeHoursAgoMillis = now.minus(Duration.ofHours(3)).toEpochMilli()
        assertEquals("3h ago", ArrTime.relative(threeHoursAgoMillis, now.toEpochMilli()))
    }

    @Test
    fun parseWireFormats() {
        assertEquals(
            Instant.parse("2026-10-20T14:30:00Z"),
            ArrTime.parse("2026-10-20T14:30:00Z"),
        )
        assertEquals(
            Instant.parse("2026-10-20T14:30:00Z"),
            ArrTime.parse("2026-10-20T16:30:00+02:00"),
        )
        assertEquals(
            Instant.parse("2026-10-20T14:30:00Z"),
            ArrTime.parse("2026-10-20T11:00:00-03:30"),
        )
        assertEquals(
            Instant.parse("2026-10-20T14:30:00.500Z"),
            ArrTime.parse("2026-10-20T14:30:00.5Z"),
        )
    }

    @Test
    fun parseLenientFallbacks() {
        assertEquals(
            Instant.parse("2026-10-20T14:30:00Z"),
            ArrTime.parse("2026-10-20T14:30:00"),
        )
        assertEquals(
            Instant.parse("2026-10-20T00:00:00Z"),
            ArrTime.parse("2026-10-20"),
        )
    }

    @Test
    fun parseRejectsBadInput() {
        assertNull(ArrTime.parse(null))
        assertNull(ArrTime.parse(""))
        assertNull(ArrTime.parse("   "))
        assertNull(ArrTime.parse("not a date"))
        assertNull(ArrTime.parse("2026-13-45T99:00:00Z"))
    }

    @Test
    fun formatWireTruncatesToSecondsUtc() {
        val instant = Instant.parse("2026-10-20T14:30:00Z")
        assertEquals("2026-10-20T14:30:00Z", ArrTime.formatWire(instant))
        val shifted = Instant.parse("2026-10-20T16:30:00.750+02:00")
        assertEquals("2026-10-20T14:30:00Z", ArrTime.formatWire(shifted))
    }

    @Test
    fun wireRoundTrip() {
        val instant = Instant.parse("2021-02-03T04:05:06Z")
        assertEquals(instant, ArrTime.parse(ArrTime.formatWire(instant)))
    }

    @Test
    fun calendarFormatsAreLocaleDriven() {
        val at = ZonedDateTime.of(2026, 10, 20, 14, 30, 0, 0, utc)
        assertEquals("Oct 20, 14:30", ArrTime.dateTimeShort(at, Locale.US))
        assertEquals("Tue, Oct 20, 2026, 14:30", ArrTime.dateTimeFull(at, Locale.US))
        assertEquals("Tue, Oct 20", ArrTime.dateShort(at, Locale.US))
    }

    @Test
    fun instantOverloadsUseSuppliedZone() {
        val instant = Instant.parse("2026-10-20T14:30:00Z")
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 14:30Z is 23:30 the same day in Tokyo; the US-locale format keeps the
        // assertion independent of CLDR month-name data.
        assertEquals("Oct 20, 23:30", ArrTime.dateTimeShort(instant, tokyo, Locale.US))
        assertEquals(
            ArrTime.dateTimeShort(instant.atZone(tokyo), Locale.US),
            ArrTime.dateTimeShort(instant, tokyo, Locale.US),
        )
        assertEquals(
            ArrTime.dateTimeFull(instant.atZone(tokyo), Locale.US),
            ArrTime.dateTimeFull(instant, tokyo, Locale.US),
        )
    }
}
