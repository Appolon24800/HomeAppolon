package dev.appolon.homelab.arr.core

import java.util.Locale
import kotlin.math.abs

/**
 * Human-readable formatting for the values the *arr APIs expose as raw
 * numbers or wire identifiers: byte sizes, runtimes, quality names.
 * All output uses [Locale.ROOT] digits so it never changes with device locale.
 */
object Humanize {

    private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    /**
     * Byte size with 1024 steps and the unit labels the *arr web UIs use
     * ("1.4 GB"). Whole bytes print as an integer, since a fraction of a byte
     * carries no information. [decimals] is clamped to 0..3. Values that
     * round up to the next unit after formatting carry over ("1024.0 GB"
     * becomes "1.0 TB").
     */
    fun bytes(value: Long, decimals: Int = 1): String {
        val digits = decimals.coerceIn(0, 3)
        if (value == 0L) return "0 B"
        val sign = if (value < 0) "-" else ""
        var scaled = abs(value).toDouble()
        var unit = 0
        while (scaled >= 1024.0 && unit < BYTE_UNITS.size - 1) {
            scaled /= 1024.0
            unit++
        }
        if (unit == 0) return sign + abs(value) + " B"
        if (String.format(Locale.ROOT, "%.${digits}f", scaled).toDouble() >= 1024.0 &&
            unit < BYTE_UNITS.size - 1
        ) {
            scaled /= 1024.0
            unit++
        }
        return sign + String.format(Locale.ROOT, "%.${digits}f ${BYTE_UNITS[unit]}", scaled)
    }

    /**
     * Runtime-style duration: "45s", "3m 42s", "1h 24m". Seconds drop out once
     * hours appear, matching how the *arr UIs show movie and episode runtimes.
     */
    fun duration(totalSeconds: Long): String {
        if (totalSeconds < 0) return "-" + duration(-totalSeconds)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
            minutes > 0 -> if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /** Player-clock duration: "3:47", "1:24:05". Used for track lengths. */
    fun clockDuration(totalSeconds: Long): String {
        if (totalSeconds < 0) return "-" + clockDuration(-totalSeconds)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    private val QUALITY_ALIASES = mapOf(
        "bluray" to "BluRay",
        "blu-ray" to "BluRay",
        "dvd" to "DVD",
        "dvdrip" to "DVDRip",
        "hdtv" to "HDTV",
        "webdl" to "WEB-DL",
        "web-dl" to "WEB-DL",
        "webrip" to "WEBRip",
        "web" to "WEB",
        "cam" to "CAM",
        "rawhd" to "RawHD",
    )

    /**
     * Display name for a quality identifier: substitutes the aliases the APIs
     * use in quality names and replaces hyphens with spaces, so "Bluray-2160p"
     * becomes "BluRay 2160p" and "WEBDL-1080p" becomes "WEB-DL 1080p".
     * Resolution tokens and unknown tokens pass through unchanged.
     */
    fun qualityName(raw: String): String = raw.trim()
        .split('-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            QUALITY_ALIASES[token.lowercase()] ?: token
        }
}
