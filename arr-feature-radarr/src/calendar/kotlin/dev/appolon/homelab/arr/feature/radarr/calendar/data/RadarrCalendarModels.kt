package dev.appolon.homelab.arr.feature.radarr.calendar.data

import dev.appolon.homelab.arr.api.radarr.api.RadarrMediaCovers
import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import dev.appolon.homelab.arr.core.ArrTime
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.Serializable

/**
 * Which release date field a calendar event comes from. A movie with distinct
 * cinema, physical, and digital dates produces one event per date, matching
 * the Radarr web calendar.
 */

/** Resized local poster variant requested for calendar row thumbnails. */
private const val CALENDAR_THUMB_SIZE_PX = 500

@Serializable
enum class CalendarEventKind {
    IN_CINEMAS,
    PHYSICAL_RELEASE,
    DIGITAL_RELEASE,
    RELEASE,
}

/**
 * Per-day availability state shown next to each entry. Derived from the event
 * date versus today and the movie's file state: a release whose date has
 * passed (or that already has a file) reads as released, a future cinema date
 * reads as in cinemas, everything else as announced.
 */
enum class CalendarAvailability { ANNOUNCED, IN_CINEMAS, RELEASED }

/**
 * One release event of one movie on one day, stored in the entity store as
 * the calendar cache. `eventDate` is a bare ISO local date (`yyyy-MM-dd`)
 * because Radarr's release fields are date-only; instants are normalized to
 * UTC dates at mapping time.
 */
@Serializable
data class RadarrCalendarEntry(
    val movieId: Int,
    val title: String? = null,
    val sortTitle: String? = null,
    val year: Int = 0,
    val overview: String? = null,
    val certification: String? = null,
    val runtime: Int = 0,
    val genres: List<String>? = null,
    val monitored: Boolean = false,
    val hasFile: Boolean = false,
    val status: MovieStatusType? = null,
    val eventDate: String,
    val eventKind: CalendarEventKind,
    /** Absolute http(s) URL or server-relative MediaCover path; resolved against the base URL at render time. */
    val posterPath: String? = null,
    /** TMDB poster path (or absolute URL) used when no local cover exists. */
    val remotePoster: String? = null,
) : Comparable<RadarrCalendarEntry> {

    override fun compareTo(other: RadarrCalendarEntry): Int = compareValuesBy(
        this,
        other,
        { (it.sortTitle ?: it.title ?: "").lowercase() },
        { it.movieId },
    )

    companion object {
        /** Stable entity id under [RadarrCalendarRepository.ENTRY_TYPE]: one row per movie and event kind. */
        fun entityId(movieId: Int, kind: CalendarEventKind): String = "$movieId:${kind.name}"
    }
}

/**
 * Bookkeeping row for one fetched window so month prefetches can check
 * freshness per window instead of per entity type.
 */
@Serializable
data class RadarrCalendarWindow(
    val start: String,
    val end: String,
    val unmonitored: Boolean,
    val fetchedAtMillis: Long,
    val entryCount: Int = 0,
)

/** Inclusive date window the calendar fetches for one month plus its grid padding. */
data class CalendarWindow(val start: LocalDate, val end: LocalDate) {

    operator fun contains(date: LocalDate): Boolean = date >= start && date <= end

    /** Window row id under [RadarrCalendarRepository.WINDOW_TYPE]. */
    fun id(unmonitored: Boolean): String = "$start:$end:$unmonitored"

    companion object {
        fun of(id: String): CalendarWindow? = runCatching {
            val parts = id.split(':')
            CalendarWindow(LocalDate.parse(parts[0]), LocalDate.parse(parts[1]))
        }.getOrNull()
    }
}

/** Maps one movie to its calendar events; movies without parseable dates map to nothing. */
fun calendarEntriesOf(movie: MovieResource): List<RadarrCalendarEntry> {
    val poster = movie.images.orEmpty().firstOrNull { it.coverType == MediaCoverTypes.POSTER }
    val localPath = poster?.url
        ?.takeIf { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }
        // Row thumbs only, so request the resized poster-500 variant.
        ?.let { RadarrMediaCovers.path(movie.id, MediaCoverTypes.POSTER, CALENDAR_THUMB_SIZE_PX) }
    val absolutePath = poster?.url?.takeIf { it.startsWith("http", ignoreCase = true) }
    val base = RadarrCalendarEntry(
        movieId = movie.id,
        title = movie.title,
        sortTitle = movie.sortTitle,
        year = movie.year,
        overview = movie.overview,
        certification = movie.certification,
        runtime = movie.runtime,
        genres = movie.genres,
        monitored = movie.monitored,
        hasFile = movie.hasFile == true,
        status = movie.status,
        posterPath = absolutePath ?: localPath,
        remotePoster = poster?.remoteUrl ?: movie.remotePoster,
        eventDate = "",
        eventKind = CalendarEventKind.RELEASE,
    )
    return listOf(
        movie.inCinemas to CalendarEventKind.IN_CINEMAS,
        movie.physicalRelease to CalendarEventKind.PHYSICAL_RELEASE,
        movie.digitalRelease to CalendarEventKind.DIGITAL_RELEASE,
        movie.releaseDate to CalendarEventKind.RELEASE,
    ).mapNotNull { (raw, kind) ->
        val date = ArrTime.parse(raw)?.atZone(ZoneOffset.UTC)?.toLocalDate() ?: return@mapNotNull null
        base.copy(eventDate = date.toString(), eventKind = kind)
    }
}

/** Availability of one entry as seen on [today]; see [CalendarAvailability]. */
fun availabilityOf(entry: RadarrCalendarEntry, today: LocalDate): CalendarAvailability {
    val date = LocalDate.parse(entry.eventDate)
    return when {
        entry.hasFile || date < today -> CalendarAvailability.RELEASED
        entry.eventKind == CalendarEventKind.IN_CINEMAS ||
            entry.status == MovieStatusType.IN_CINEMAS -> CalendarAvailability.IN_CINEMAS
        else -> CalendarAvailability.ANNOUNCED
    }
}

/**
 * Count of distinct movies with any event in `[today, today + horizonDays]`,
 * the number the shell shows as its upcoming badge.
 */
fun countUpcoming(entries: List<RadarrCalendarEntry>, today: LocalDate, horizonDays: Int): Int {
    val last = today.plusDays(horizonDays.toLong())
    return entries.asSequence()
        .mapNotNull { entry -> runCatching { LocalDate.parse(entry.eventDate) }.getOrNull()?.let { it to entry.movieId } }
        .filter { (date, _) -> date >= today && date <= last }
        .map { (_, movieId) -> movieId }
        .distinct()
        .count()
}
