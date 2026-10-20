package dev.appolon.homelab.arr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun dataRateZeroAndNegativeShowNothingMoving() {
        assertEquals("0 B/s", formatDataRate(0))
        assertEquals("0 B/s", formatDataRate(-5))
    }

    @Test
    fun dataRateBelowOneKilobyteStaysInBytes() {
        assertEquals("512 B/s", formatDataRate(512))
        assertEquals("1023 B/s", formatDataRate(1023))
    }

    @Test
    fun dataRateStepsBinaryUnits() {
        assertEquals("1.0 KB/s", formatDataRate(1024))
        assertEquals("1.5 MB/s", formatDataRate(1_572_864))
        assertEquals("1.1 GB/s", formatDataRate(1_181_116_109))
    }

    @Test
    fun dataRateDropsDecimalsAboveOneHundred() {
        // 153,600,000 B/s = 146.48 MB/s -> no decimal to keep text width steady.
        assertEquals("146 MB/s", formatDataRate(153_600_000))
    }

    @Test
    fun etaBelowOneMinuteShowsSeconds() {
        assertEquals("0 s", formatEta(0))
        assertEquals("45 s", formatEta(45))
    }

    @Test
    fun etaBelowOneHourShowsMinutesAndRestSeconds() {
        assertEquals("1 min", formatEta(60))
        assertEquals("1 min 30 s", formatEta(90))
        assertEquals("59 min 59 s", formatEta(3599))
    }

    @Test
    fun etaAtOrAboveOneHourShowsHoursAndRestMinutes() {
        assertEquals("1 h", formatEta(3600))
        assertEquals("1 h 12 min", formatEta(4320))
        assertEquals("23 h 59 min", formatEta(86_399))
        assertEquals("25 h", formatEta(90_000))
    }

    @Test
    fun monogramTakesFirstLetterAndHandlesBlankTitles() {
        assertEquals("D", posterMonogram("Dune"))
        assertEquals("D", posterMonogram("  dune "))
        assertEquals("?", posterMonogram(""))
        assertEquals("?", posterMonogram("   "))
    }
}
