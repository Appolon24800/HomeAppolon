package dev.appolon.homelab.arr.feature.sonarr.calendar.data

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.core.ArrImages
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.CoverType
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * Airing state of one calendar entry as seen on a given day. Episodes are
 * day-granular in the Sonarr calendar (the API's `airDate`), so the boundary
 * is the calendar day, not the air instant.
 */
enum class CalendarAirState { AIRED, NOT_YET_AIRED }

/**
 * One episode airing on one day, stored in the entity store as the calendar
 * cache at episode granularity. `airDate` is a bare ISO local date
 * (`yyyy-MM-dd`), exactly the wire form Sonarr reports; `airDateUtc` is kept
 * raw because ordering within a day and future time display need the instant.
 * `finaleType` is cached even though the calendar does not badge it yet, so
 * later renders can badge finales without a cache purge.
 */
@Serializable
data class SonarrCalendarEntry(
    val episodeId: Int,
    val seriesId: Int,
    val seriesTitle: String? = null,
    val sortTitle: String? = null,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val absoluteEpisodeNumber: Int? = null,
    val title: String? = null,
    val overview: String? = null,
    val runtime: Int = 0,
    val finaleType: String? = null,
    val airDate: String,
    val airDateUtc: String? = null,
    val monitored: Boolean = false,
    val seriesMonitored: Boolean = false,
    val hasFile: Boolean = false,
    /** Absolute http(s) URL or server-relative MediaCover path; resolved against the base URL at render time. */
    val posterPath: String? = null,
    /** Remote poster path (or absolute URL) used when no local cover exists. */
    val remotePoster: String? = null,
) : Comparable<SonarrCalendarEntry> {

    /** `SxxEyy` episode code, locale-neutral so it sorts and reads identically everywhere. */
    val episodeCode: String
        get() = episodeCodeOf(seasonNumber, episodeNumber)

    override fun compareTo(other: SonarrCalendarEntry): Int = compareValuesBy(
        this,
        other,
        { (it.sortTitle ?: it.seriesTitle ?: "").lowercase(Locale.ROOT) },
        { it.seasonNumber },
        { it.episodeNumber },
        { it.episodeId },
    )

    companion object {
        /** Stable entity id under [SonarrCalendarRepository.ENTRY_TYPE]: one row per episode. */
        fun entityId(episodeId: Int): String = episodeId.toString()
    }
}

/** `S01E05`-shaped code; season 0 renders as `S00Eyy`, matching Sonarr's specials numbering. */
fun episodeCodeOf(seasonNumber: Int, episodeNumber: Int): String =
    String.format(Locale.ROOT, "S%02dE%02d", seasonNumber, episodeNumber)

/**
 * Maps one episode to its calendar entry, or null when its air date is absent
 * or unparseable (TBA episodes never reach the calendar). Series fields come
 * from the embedded `series` resource the calendar endpoint returns with
 * `includeSeries=true`; the poster prefers the server's local MediaCover path
 * and falls back to remote art.
 */
fun calendarEntryOf(episode: EpisodeResource): SonarrCalendarEntry? {
    val airDate = ArrTime.parse(episode.airDate)
        ?.atZone(ZoneOffset.UTC)?.toLocalDate()
        ?: return null
    val series = episode.series
    val seriesId = if (series.id != 0) series.id else episode.seriesId
    val poster = series.images.orEmpty().firstOrNull { it.coverType == MediaCoverTypes.POSTER }
    val localPath = poster?.url
        ?.takeIf { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }
        ?.let { ArrImages.sonarrCover(seriesId.toLong(), CoverType.POSTER) }
    val absolutePath = poster?.url?.takeIf { it.startsWith("http", ignoreCase = true) }
    return SonarrCalendarEntry(
        episodeId = episode.id,
        seriesId = seriesId,
        seriesTitle = series.title,
        sortTitle = series.sortTitle,
        seasonNumber = episode.seasonNumber,
        episodeNumber = episode.episodeNumber,
        absoluteEpisodeNumber = episode.absoluteEpisodeNumber,
        title = episode.title,
        overview = episode.overview,
        runtime = episode.runtime,
        finaleType = episode.finaleType,
        airDate = airDate.toString(),
        airDateUtc = episode.airDateUtc,
        monitored = episode.monitored,
        seriesMonitored = series.monitored,
        hasFile = episode.hasFile,
        posterPath = absolutePath ?: localPath,
        remotePoster = poster?.remoteUrl ?: series.remotePoster,
    )
}

/** Airing state of one entry on [today]; unaired entries dim in the UI. */
fun airStateOf(entry: SonarrCalendarEntry, today: LocalDate): CalendarAirState {
    val date = LocalDate.parse(entry.airDate)
    return if (date < today) CalendarAirState.AIRED else CalendarAirState.NOT_YET_AIRED
}

/**
 * Count of distinct series with a monitored episode airing in
 * `[today, today + horizonDays]`, the number the shell shows as its upcoming
 * badge. Multiple episodes of one series inside the horizon count once.
 */
fun countUpcoming(entries: List<SonarrCalendarEntry>, today: LocalDate, horizonDays: Int): Int {
    val last = today.plusDays(horizonDays.toLong())
    return entries.asSequence()
        .filter { it.monitored }
        .mapNotNull { entry ->
            runCatching { LocalDate.parse(entry.airDate) }.getOrNull()?.let { it to entry.seriesId }
        }
        .filter { (date, _) -> date >= today && date <= last }
        .map { (_, seriesId) -> seriesId }
        .distinct()
        .count()
}
