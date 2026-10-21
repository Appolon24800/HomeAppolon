package dev.appolon.homelab.arr.feature.lidarr.settings.system.data

/**
 * Pure text helpers for the settings-system screens. Everything here is
 * deterministic and side-effect free so the ViewModels and unit tests share
 * one definition of how wire values become display strings.
 */
object SystemText {

    /**
     * Parses a .NET time-span string such as `00:00:01.7431281` (what the
     * API reports as a task's `lastDuration`) into seconds. Fractional
     * parts are truncated. Returns null for null, blank, or unparseable
     * input so callers can fall back to hiding the duration.
     */
    fun dotNetDurationSeconds(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().split(":")
        if (parts.size !in 2..3) return null
        val hours = parts.getOrNull(parts.size - 3)?.toLongOrNull() ?: 0L
        val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: return null
        val secondsPart = parts.last()
        val seconds = secondsPart.substringBefore('.').toLongOrNull() ?: return null
        if (minutes !in 0..59 || seconds !in 0..59) return null
        return hours * 3600 + minutes * 60 + seconds
    }

    /**
     * Humanizes a scheduled task interval given in minutes. Days are used
     * when the value divides evenly by 1440, hours when it divides by 60,
     * otherwise minutes, matching how the Lidarr UI presents task cadence.
     */
    fun intervalMinutes(minutes: Int): String = when {
        minutes <= 0 -> "0m"
        minutes % 1440 == 0 -> "${minutes / 1440}d"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes}m"
    }

    /** Lidarr's documented SSL settings anchor, linked from the general screen. */
    const val LIDARR_SSL_DOC_URL = "https://wiki.servarr.com/lidarr/settings#ssl"
}
