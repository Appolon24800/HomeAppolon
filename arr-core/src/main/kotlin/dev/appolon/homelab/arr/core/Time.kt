package dev.appolon.homelab.arr.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * Time parsing and formatting for the arr screens. All functions are
 * deterministic: the reference clock is an explicit argument defaulting to now,
 * and locale-aware formats take a [Locale] so tests stay stable. English text
 * ("ago", "in") is baked in; screens needing translated relative strings
 * format from the raw duration themselves.
 */
object ArrTime {

    /** Serializes to the *arr wire format: offset date-time, second precision, UTC. */
    fun formatWire(instant: Instant): String =
        instant.truncatedTo(ChronoUnit.SECONDS).atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /**
     * Parses a *arr wire date-time. Primary form is the offset date-time the
     * APIs return (`2026-10-20T14:30:00Z`, `2026-10-20T14:30:00+02:00`), with
     * a fractional second tolerated. Two lenient fallbacks cover fields that
     * ship without an offset: offset-less date-time read as UTC, and bare
     * dates at start of day UTC (release and air date fields). Returns null
     * for null, blank, or unparseable input so DTO mappers can treat missing
     * dates as absent instead of throwing.
     */
    fun parse(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        runCatching { OffsetDateTime.parse(trimmed).toInstant() }
            .getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC) }
            .getOrNull()?.let { return it }
        return runCatching { LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant() }
            .getOrNull()
    }

    /** Compact relative time: "3h ago", "in 2d", "just now" under one minute. */
    fun relative(instant: Instant, now: Instant = Instant.now()): String =
        relativeFromSeconds(Duration.between(now, instant).seconds)

    /** [relative] over epoch milliseconds, the shape most DTOs carry. */
    fun relative(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String =
        relativeFromSeconds((epochMillis - nowMillis) / 1000L)

    private fun relativeFromSeconds(deltaSeconds: Long): String {
        val future = deltaSeconds > 0
        val s = abs(deltaSeconds)
        val magnitude = when {
            s < 60L -> return "just now"
            s < 3_600L -> "${s / 60}m"
            s < 86_400L -> "${s / 3_600}h"
            s < 604_800L -> "${s / 86_400}d"
            s < 2_592_000L -> "${s / 604_800}w"
            s < 31_536_000L -> "${s / 2_592_000}mo"
            else -> "${s / 31_536_000}y"
        }
        return if (future) "in $magnitude" else "$magnitude ago"
    }

    /** Agenda rows: "Oct 20, 14:30". */
    fun dateTimeShort(at: ZonedDateTime, locale: Locale = Locale.getDefault()): String =
        at.format(DateTimeFormatter.ofPattern("MMM d, HH:mm", locale))

    /** [dateTimeShort] for callers holding a raw instant. */
    fun dateTimeShort(
        at: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = dateTimeShort(at.atZone(zone), locale)

    /** Detail headers and reminders: "Tue, Oct 20, 2026, 14:30". */
    fun dateTimeFull(at: ZonedDateTime, locale: Locale = Locale.getDefault()): String =
        at.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy, HH:mm", locale))

    /** [dateTimeFull] for callers holding a raw instant. */
    fun dateTimeFull(
        at: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = dateTimeFull(at.atZone(zone), locale)

    /** Calendar day headers: "Tue, Oct 20". */
    fun dateShort(at: ZonedDateTime, locale: Locale = Locale.getDefault()): String =
        at.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
}
