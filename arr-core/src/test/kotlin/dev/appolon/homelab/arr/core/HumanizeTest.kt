package dev.appolon.homelab.arr.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HumanizeTest {

    @Test
    fun bytesSteps() {
        assertEquals("0 B", Humanize.bytes(0))
        assertEquals("512 B", Humanize.bytes(512))
        assertEquals("1.0 KB", Humanize.bytes(1024))
        assertEquals("1.5 KB", Humanize.bytes(1536))
        assertEquals("1.5 MB", Humanize.bytes((1024.0 * 1024 * 1.5).toLong()))
        assertEquals("3.7 GB", Humanize.bytes((1024.0 * 1024 * 1024 * 3.7).toLong()))
        assertEquals("1.4 TB", Humanize.bytes((1024.0 * 1024 * 1024 * 1024 * 1.4).toLong()))
    }

    @Test
    fun bytesNegativeAndDecimals() {
        assertEquals("-1.5 KB", Humanize.bytes(-1536))
        assertEquals("2 KB", Humanize.bytes(1536, decimals = 0))
        assertEquals("1.500 KB", Humanize.bytes(1536, decimals = 3))
        // Decimals clamp instead of throwing.
        assertEquals("1.500 KB", Humanize.bytes(1536, decimals = 9))
    }

    @Test
    fun bytesCarriesRoundingOverflowToNextUnit() {
        // One TiB minus one byte formats as "1024.0 GB" before the carry fix-up.
        assertEquals("1.0 TB", Humanize.bytes(1024L * 1024 * 1024 * 1024 - 1))
    }

    @Test
    fun durationSteps() {
        assertEquals("0s", Humanize.duration(0))
        assertEquals("45s", Humanize.duration(45))
        assertEquals("3m", Humanize.duration(180))
        assertEquals("3m 42s", Humanize.duration(222))
        assertEquals("1h", Humanize.duration(3600))
        assertEquals("1h 24m", Humanize.duration(5085))
        assertEquals("24h", Humanize.duration(86_400))
        assertEquals("-3m 42s", Humanize.duration(-222))
    }

    @Test
    fun clockDurationSteps() {
        assertEquals("0:00", Humanize.clockDuration(0))
        assertEquals("3:47", Humanize.clockDuration(227))
        assertEquals("1:24:05", Humanize.clockDuration(5045))
        assertEquals("-3:47", Humanize.clockDuration(-227))
    }

    @Test
    fun qualityNameAliases() {
        assertEquals("BluRay 2160p", Humanize.qualityName("Bluray-2160p"))
        assertEquals("WEB-DL 1080p", Humanize.qualityName("WEBDL-1080p"))
        assertEquals("WEBRip 720p", Humanize.qualityName("WEBRip-720p"))
        assertEquals("HDTV 1080p", Humanize.qualityName("HDTV-1080p"))
        assertEquals("DVDRip 480p", Humanize.qualityName("DVDrip-480p"))
    }

    @Test
    fun qualityNamePassthrough() {
        assertEquals("Unknown", Humanize.qualityName("Unknown"))
        assertEquals("Remux 2160p", Humanize.qualityName("Remux-2160p"))
        assertEquals("Raw HD", Humanize.qualityName("Raw-HD"))
        assertEquals("2160p", Humanize.qualityName("-2160p-"))
    }
}
