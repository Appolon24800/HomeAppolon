package dev.appolon.homelab.arr.feature.lidarr.calendar.data

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.core.ArrImages
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.CoverType
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * Release state of one calendar entry as seen on a given day. Album releases
 * are day-granular in the Lidarr calendar (the API's `releaseDate`), so the
 * boundary is the calendar day, not a release instant.
 */
enum class CalendarReleaseState { RELEASED, UPCOMING }

/**
 * One album releasing on one day, stored in the entity store as the calendar
 * cache at album-release granularity. `releaseDate` is a bare ISO local date
 * (`yyyy-MM-dd`). The full release description Lidarr shows is the primary
 * `albumType` plus any `secondaryTypes` (for example `Album` + `Deluxe`),
 * both cached so type badges never need a refetch.
 */
@Serializable
data class LidarrCalendarEntry(
    val albumId: Int,
    val artistId: Int,
    val artistTitle: String? = null,
    val sortTitle: String? = null,
    val albumTitle: String? = null,
    val disambiguation: String? = null,
    /** Primary release type: Album, Single, EP, and Lidarr's other MusicBrainz types. */
    val albumType: String? = null,
    /** Secondary release types (Deluxe, Remastered, and friends), wire strings. */
    val secondaryTypes: List<String> = emptyList(),
    val releaseDate: String,
    val monitored: Boolean = false,
    val artistMonitored: Boolean = false,
    val trackCount: Int = 0,
    val trackFileCount: Int = 0,
    val durationMillis: Long = 0,
    /** Server-relative album cover path resolved against the base URL at render time. */
    val coverPath: String? = null,
    /** Remote cover path (or absolute URL) used when no local cover exists. */
    val remoteCover: String? = null,
) : Comparable<LidarrCalendarEntry> {

    override fun compareTo(other: LidarrCalendarEntry): Int = compareValuesBy(
        this,
        other,
        { (it.sortTitle ?: it.artistTitle ?: "").lowercase(Locale.ROOT) },
        { it.albumId },
    )

    companion object {
        /** Stable entity id under [LidarrCalendarRepository.ENTRY_TYPE]: one row per album. */
        fun entityId(albumId: Int): String = albumId.toString()
    }
}

/**
 * Maps one album to its calendar entry, or null when the release date is
 * absent, unparseable, or a MusicBrainz placeholder (`0001-01-01`, which
 * Lidarr sends for albums without an announced date). Artist fields come
 * from the embedded `artist` resource the calendar endpoint returns with
 * `includeArtist=true`; the cover prefers the server's local album cover
 * path and falls back to remote art.
 */
fun calendarEntryOf(album: AlbumResource): LidarrCalendarEntry? {
    val releaseDate = ArrTime.parse(album.releaseDate)
        ?.atZone(ZoneOffset.UTC)?.toLocalDate()
        ?: return null
    if (releaseDate.year <= 1) return null
    val artist = album.artist
    val artistId = if (artist?.id != 0 && artist != null) artist.id else album.artistId
    val cover = album.images.orEmpty().firstOrNull { it.coverType == MediaCoverTypes.COVER }
    val localPath = cover?.url
        ?.takeIf { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }
        ?.let { ArrImages.lidarrAlbumCover(album.id.toLong(), CoverType.COVER) }
    val absolutePath = cover?.url?.takeIf { it.startsWith("http", ignoreCase = true) }
    return LidarrCalendarEntry(
        albumId = album.id,
        artistId = artistId,
        artistTitle = artist?.artistName,
        sortTitle = artist?.sortName,
        albumTitle = album.title,
        disambiguation = album.disambiguation?.takeIf { it.isNotEmpty() },
        albumType = album.albumType?.takeIf { it.isNotEmpty() },
        secondaryTypes = album.secondaryTypes.orEmpty().filter { it.isNotEmpty() },
        releaseDate = releaseDate.toString(),
        monitored = album.monitored,
        artistMonitored = artist?.monitored ?: false,
        trackCount = album.statistics?.totalTrackCount ?: 0,
        trackFileCount = album.statistics?.trackFileCount ?: 0,
        durationMillis = album.duration * 1000L,
        coverPath = absolutePath ?: localPath,
        remoteCover = cover?.remoteUrl ?: album.remoteCover,
    )
}

/** Release state of one entry on [today]; upcoming entries dim in the UI. */
fun releaseStateOf(entry: LidarrCalendarEntry, today: LocalDate): CalendarReleaseState {
    val date = LocalDate.parse(entry.releaseDate)
    return if (date < today) CalendarReleaseState.RELEASED else CalendarReleaseState.UPCOMING
}

/**
 * Count of distinct artists with a monitored album releasing in
 * `[today, today + horizonDays]`, the number the shell shows as its upcoming
 * badge. Multiple albums of one artist inside the horizon count once.
 */
fun countUpcoming(entries: List<LidarrCalendarEntry>, today: LocalDate, horizonDays: Int): Int {
    val last = today.plusDays(horizonDays.toLong())
    return entries.asSequence()
        .filter { it.monitored }
        .mapNotNull { entry ->
            runCatching { LocalDate.parse(entry.releaseDate) }.getOrNull()
                ?.let { it to entry.artistId }
        }
        .filter { (date, _) -> date >= today && date <= last }
        .map { (_, artistId) -> artistId }
        .distinct()
        .count()
}
