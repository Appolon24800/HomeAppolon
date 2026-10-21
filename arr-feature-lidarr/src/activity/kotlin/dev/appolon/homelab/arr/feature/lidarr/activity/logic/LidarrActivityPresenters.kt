package dev.appolon.homelab.arr.feature.lidarr.activity.logic

import dev.appolon.homelab.arr.api.lidarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.Humanize
import java.time.Instant

/** Event-type slices the history feed offers as filter chips. */
enum class LidarrHistoryFilter {
    ALL,

    /** Grabbed from an indexer. */
    GRABBED,

    /** Every import variant: manual track import, artist folder, and finished download. */
    IMPORTED,

    /** Download failures, incomplete album imports, and ignored downloads. */
    FAILED,

    /** A track file was renamed. */
    RENAMED,

    /** A track file was retagged (metadata written to disk). */
    RETAGGED,

    /** A track file was deleted. */
    DELETED,

    /** Events that carry an album context, regardless of type. */
    ALBUM_EVENTS,

    /** Events that operate at the artist level: folder imports, renames, retags. */
    ARTIST_EVENTS,
    ;

    /** Wire names this filter matches; empty means the filter matches by scope instead. */
    internal val wireNames: Set<String>
        get() = when (this) {
            GRABBED -> setOf("grabbed")
            IMPORTED -> setOf("trackFileImported", "artistFolderImported", "downloadImported")
            FAILED -> setOf("downloadFailed", "albumImportIncomplete", "downloadIgnored")
            RENAMED -> setOf("trackFileRenamed")
            RETAGGED -> setOf("trackFileRetagged")
            DELETED -> setOf("trackFileDeleted")
            else -> emptySet()
        }
}

/**
 * Date windows for the feed. Null duration means all time. Durations are
 * subtracted from "now" at filter time, so chips behave like the web UI's
 * quick filters rather than calendar-day boundaries.
 */
enum class LidarrHistoryDateWindow(val durationMillis: Long?) {
    ALL_TIME(null),
    LAST_24_HOURS(24 * 60 * 60 * 1000L),
    LAST_7_DAYS(7 * 24 * 60 * 60 * 1000L),
    LAST_30_DAYS(30L * 24 * 60 * 60 * 1000L),
}

/** One history event ready for rendering; no Android or Compose types. */
data class LidarrHistoryEventUi(
    val id: Int,
    val eventType: EntityHistoryEventType,
    val albumId: Int,
    val artistId: Int,
    /** Row title: the artist when Lidarr matched one, else the release title. */
    val title: String,
    val albumTitle: String?,
    val trackTitle: String?,
    /** "Track 5" style label when the embedded track carries a number. */
    val trackLabel: String?,
    val sourceTitle: String?,
    val date: Instant?,
    val relativeDate: String?,
    val fullDate: String?,
    val qualityName: String?,
    val source: String?,
    val ageText: String?,
    val indexer: String?,
    val downloadClient: String?,
    val sizeText: String?,
    val releaseGroup: String?,
    val customFormatNames: List<String>,
    val customFormatScore: Int,
    val message: String?,
    val downloadId: String?,
    /** Grabbed events can be marked failed manually. */
    val canMarkFailed: Boolean,
    /** Failed or incomplete imports with a download id can retry through manual import. */
    val failedImport: Boolean,
    val queuedOffline: Boolean = false,
)

/** One blocklist entry ready for rendering; no Android or Compose types. */
data class LidarrBlocklistEntryUi(
    val id: Int,
    val artistId: Int,
    val title: String,
    val sourceTitle: String?,
    val date: Instant?,
    val relativeDate: String?,
    val qualityName: String?,
    val protocol: DownloadProtocol,
    val indexer: String?,
    /** Lidarr's reason for the rejection (the `message` column). */
    val reason: String?,
    val customFormatNames: List<String>,
    val queuedOffline: Boolean = false,
)

/**
 * Pure presentation for the Lidarr activity screens: wire resources in,
 * album-granular row models and filter rules out. The committed DTOs embed
 * artist, album, and track as typed resources, so label extraction reads
 * those directly, free of framework types and directly unit-testable per
 * the architecture doc.
 */
object LidarrActivityPresenters {

    /** Matched artist title from the embedded artist object, when present. */
    fun artistTitleOf(event: HistoryResource): String? =
        event.artist?.artistName?.takeIf { it.isNotEmpty() }

    /** Row title: the artist when Lidarr matched one, else the release title. */
    fun titleOf(event: HistoryResource): String =
        artistTitleOf(event) ?: event.sourceTitle.orEmpty().ifEmpty { UNKNOWN_TITLE }

    fun present(
        event: HistoryResource,
        queuedOffline: Boolean = false,
        now: Instant = Instant.now(),
    ): LidarrHistoryEventUi = LidarrHistoryEventUi(
        id = event.id,
        eventType = event.eventType,
        albumId = event.albumId,
        artistId = event.artistId,
        title = titleOf(event),
        albumTitle = event.album?.title?.takeIf { it.isNotEmpty() },
        trackTitle = event.track?.title?.takeIf { it.isNotEmpty() },
        trackLabel = event.track?.trackNumber?.takeIf { it.isNotEmpty() }
            ?.let { number -> "Track $number" },
        sourceTitle = event.sourceTitle?.takeIf { it.isNotEmpty() },
        date = ArrTime.parse(event.date),
        relativeDate = ArrTime.parse(event.date)?.let { ArrTime.relative(it, now) },
        fullDate = ArrTime.parse(event.date)?.let { ArrTime.dateTimeFull(it) },
        qualityName = event.quality?.quality?.name?.let(Humanize::qualityName),
        source = dataValue(event, "source"),
        ageText = ageText(event),
        indexer = dataValue(event, "indexer"),
        downloadClient = dataValue(event, "downloadClient"),
        sizeText = dataValue(event, "size")?.toLongOrNull()?.let(Humanize::bytes),
        releaseGroup = dataValue(event, "releaseGroup"),
        customFormatNames = event.customFormats.orEmpty()
            .mapNotNull { it.name?.takeIf(String::isNotEmpty) },
        customFormatScore = event.customFormatScore,
        message = dataValue(event, "message") ?: dataValue(event, "reason"),
        downloadId = event.downloadId?.takeIf { it.isNotEmpty() },
        canMarkFailed = event.eventType == EntityHistoryEventType.GRABBED && event.id > 0,
        failedImport = (
            event.eventType == EntityHistoryEventType.DOWNLOAD_FAILED ||
                event.eventType == EntityHistoryEventType.ALBUM_IMPORT_INCOMPLETE
            ) && !event.downloadId.isNullOrEmpty(),
        queuedOffline = queuedOffline,
    )

    fun present(
        events: List<HistoryResource>,
        queuedIds: Set<Int> = emptySet(),
        now: Instant = Instant.now(),
    ): List<LidarrHistoryEventUi> = events
        .sortedWith(compareByDescending<HistoryResource> { ArrTime.parse(it.date) ?: Instant.EPOCH }
            .thenByDescending { it.id })
        .map { present(it, queuedOffline = it.id in queuedIds, now = now) }

    fun presentBlocklist(
        entry: BlocklistResource,
        queuedOffline: Boolean = false,
        now: Instant = Instant.now(),
    ): LidarrBlocklistEntryUi = LidarrBlocklistEntryUi(
        id = entry.id,
        artistId = entry.artistId,
        title = entry.artist?.artistName?.takeIf { it.isNotEmpty() }
            ?: entry.sourceTitle.orEmpty().ifEmpty { UNKNOWN_TITLE },
        sourceTitle = entry.sourceTitle?.takeIf { it.isNotEmpty() },
        date = ArrTime.parse(entry.date),
        relativeDate = ArrTime.parse(entry.date)?.let { ArrTime.relative(it, now) },
        qualityName = entry.quality?.quality?.name?.let(Humanize::qualityName),
        protocol = entry.protocol,
        indexer = entry.indexer?.takeIf { it.isNotEmpty() },
        reason = entry.message?.takeIf { it.isNotEmpty() },
        customFormatNames = entry.customFormats.orEmpty()
            .mapNotNull { it.name?.takeIf(String::isNotEmpty) },
        queuedOffline = queuedOffline,
    )

    fun presentBlocklist(
        entries: List<BlocklistResource>,
        queuedIds: Set<Int> = emptySet(),
        now: Instant = Instant.now(),
    ): List<LidarrBlocklistEntryUi> = entries
        .sortedWith(compareByDescending<BlocklistResource> { ArrTime.parse(it.date) ?: Instant.EPOCH }
            .thenByDescending { it.id })
        .map { presentBlocklist(it, queuedOffline = it.id in queuedIds, now = now) }

    /**
     * Applies every feed filter at once. Input order is the presenter's
     * date-descending order, not the store's insertion order.
     */
    fun filter(
        events: List<LidarrHistoryEventUi>,
        filter: LidarrHistoryFilter,
        window: LidarrHistoryDateWindow,
        query: String,
        now: Instant,
    ): List<LidarrHistoryEventUi> = events.filter { event ->
        matchesType(event, filter) &&
            matchesWindowDate(event.date, window, now) &&
            matchesQueryText(event, query)
    }

    /**
     * True when scrolling further could still reveal in-window events, so
     * the load-more trigger should keep firing. The list is date-descending:
     * once the oldest loaded row predates the window start, every later page
     * is strictly older and fetching can stop even before the server's end.
     */
    fun needsOlderPages(
        events: List<LidarrHistoryEventUi>,
        window: LidarrHistoryDateWindow,
        endReached: Boolean,
        now: Instant,
    ): Boolean {
        if (endReached) return false
        val duration = window.durationMillis ?: return true
        val oldest = events.minOfOrNull { it.date ?: Instant.EPOCH } ?: return true
        return oldest >= now.minusMillis(duration)
    }

    /** Wire-name slices match the event type; scope slices match the event's context. */
    private fun matchesType(event: LidarrHistoryEventUi, filter: LidarrHistoryFilter): Boolean =
        when (filter) {
            LidarrHistoryFilter.ALL -> true
            LidarrHistoryFilter.ALBUM_EVENTS -> event.albumId > 0 || event.albumTitle != null
            LidarrHistoryFilter.ARTIST_EVENTS -> event.eventType in ARTIST_LEVEL_TYPES
            else -> event.eventType.toString() in filter.wireNames
        }

    private fun matchesWindowDate(
        date: Instant?,
        window: LidarrHistoryDateWindow,
        now: Instant,
    ): Boolean {
        val duration = window.durationMillis ?: return true
        val at = date ?: return false
        return at >= now.minusMillis(duration)
    }

    /** The query matches the artist, the album, the track, or the release name. */
    private fun matchesQueryText(event: LidarrHistoryEventUi, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return event.title.contains(needle, ignoreCase = true) ||
            event.albumTitle?.contains(needle, ignoreCase = true) == true ||
            event.trackTitle?.contains(needle, ignoreCase = true) == true ||
            event.sourceTitle?.contains(needle, ignoreCase = true) == true
    }

    private fun dataValue(event: HistoryResource, key: String): String? =
        event.data?.get(key)?.takeIf { !it.isNullOrEmpty() }

    /**
     * Lidarr reports age in the data map as fractional days, whole hours, or
     * whole minutes depending on endpoint version; prefer the finest unit
     * present and render a human duration.
     */
    private fun ageText(event: HistoryResource): String? {
        dataValue(event, "age")?.toDoubleOrNull()?.let { days ->
            if (days >= 0.0) return Humanize.duration((days * 86_400).toLong())
        }
        dataValue(event, "ageHours")?.toDoubleOrNull()?.let { hours ->
            if (hours >= 0.0) return Humanize.duration((hours * 3_600).toLong())
        }
        dataValue(event, "ageMinutes")?.toDoubleOrNull()?.let { minutes ->
            if (minutes >= 0.0) return Humanize.duration((minutes * 60).toLong())
        }
        return null
    }

    /** Event types whose subject is the artist's files rather than one album. */
    private val ARTIST_LEVEL_TYPES = setOf(
        EntityHistoryEventType.ARTIST_FOLDER_IMPORTED,
        EntityHistoryEventType.TRACK_FILE_RENAMED,
        EntityHistoryEventType.TRACK_FILE_RETAGGED,
        EntityHistoryEventType.TRACK_FILE_DELETED,
    )

    private const val UNKNOWN_TITLE = "—"
}
