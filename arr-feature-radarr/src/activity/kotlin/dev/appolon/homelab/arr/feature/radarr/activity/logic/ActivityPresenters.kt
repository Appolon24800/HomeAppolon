package dev.appolon.homelab.arr.feature.radarr.activity.logic

import dev.appolon.homelab.arr.api.radarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.HistoryResource
import dev.appolon.homelab.arr.api.radarr.model.MovieHistoryEventType
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.Humanize
import java.time.Instant

/** Event-type slices the history feed offers as filter chips. */
enum class HistoryEventTypeFilter(val wireNames: Set<String>) {
    ALL(emptySet()),

    /** Grabbed from an indexer. */
    GRABBED(setOf("grabbed")),

    /** Both import variants: download folder and movie folder. */
    IMPORTED(setOf("downloadFolderImported", "movieFolderImported")),

    /** Download failed or ignored by the user. */
    FAILED(setOf("downloadFailed", "downloadIgnored")),

    /** A movie file was deleted. */
    DELETED(setOf("movieFileDeleted")),

    /** A movie file was renamed. */
    RENAMED(setOf("movieFileRenamed")),
}

/**
 * Date windows for the feed. Null duration means all time. Durations are
 * subtracted from "now" at filter time, so chips behave like the web UI's
 * quick filters rather than calendar-day boundaries.
 */
enum class HistoryDateWindow(val durationMillis: Long?) {
    ALL_TIME(null),
    LAST_24_HOURS(24 * 60 * 60 * 1000L),
    LAST_7_DAYS(7 * 24 * 60 * 60 * 1000L),
    LAST_30_DAYS(30L * 24 * 60 * 60 * 1000L),
}

/** One history event ready for rendering; no Android or Compose types. */
data class RadarrHistoryEventUi(
    val id: Int,
    val eventType: MovieHistoryEventType,
    val movieId: Int,
    val title: String,
    val sourceTitle: String?,
    val movieYear: Int?,
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
    /** Failed downloads with a download id can retry through manual import. */
    val failedImport: Boolean,
    val queuedOffline: Boolean = false,
)

/** One blocklist entry ready for rendering; no Android or Compose types. */
data class RadarrBlocklistEntryUi(
    val id: Int,
    val movieId: Int,
    val title: String,
    val movieYear: Int?,
    val sourceTitle: String?,
    val date: Instant?,
    val relativeDate: String?,
    val qualityName: String?,
    val protocol: DownloadProtocol,
    val indexer: String?,
    /** Radarr's reason for the rejection (the `message` column). */
    val reason: String?,
    val customFormatNames: List<String>,
    val languageNames: List<String>,
    val queuedOffline: Boolean = false,
)

/**
 * Pure presentation for the Radarr activity screens: wire resources in,
 * row models and filter rules out. Kept free of framework types so every
 * rule here is directly unit-testable, per the architecture doc.
 */
object ActivityPresenters {

    /** Row title: the movie when Radarr matched one, else the release title. */
    fun titleOf(event: HistoryResource): String =
        event.movie.title?.takeIf { it.isNotEmpty() }
            ?: event.sourceTitle.orEmpty().ifEmpty { UNKNOWN_TITLE }

    fun present(
        event: HistoryResource,
        queuedOffline: Boolean = false,
        now: Instant = Instant.now(),
    ): RadarrHistoryEventUi = RadarrHistoryEventUi(
        id = event.id,
        eventType = event.eventType,
        movieId = event.movieId,
        title = titleOf(event),
        sourceTitle = event.sourceTitle?.takeIf { it.isNotEmpty() },
        movieYear = event.movie.year.takeIf { it > 0 },
        date = ArrTime.parse(event.date),
        relativeDate = ArrTime.parse(event.date)?.let { ArrTime.relative(it, now) },
        fullDate = ArrTime.parse(event.date)?.let { ArrTime.dateTimeFull(it) },
        qualityName = event.quality.quality?.name?.let(Humanize::qualityName),
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
        canMarkFailed = event.eventType == MovieHistoryEventType.GRABBED && event.id > 0,
        failedImport = event.eventType == MovieHistoryEventType.DOWNLOAD_FAILED &&
            !event.downloadId.isNullOrEmpty(),
        queuedOffline = queuedOffline,
    )

    fun present(
        events: List<HistoryResource>,
        queuedIds: Set<Int> = emptySet(),
        now: Instant = Instant.now(),
    ): List<RadarrHistoryEventUi> = events
        .sortedWith(compareByDescending<HistoryResource> { ArrTime.parse(it.date) ?: Instant.EPOCH }
            .thenByDescending { it.id })
        .map { present(it, queuedOffline = it.id in queuedIds, now = now) }

    fun presentBlocklist(
        entry: BlocklistResource,
        queuedOffline: Boolean = false,
        now: Instant = Instant.now(),
    ): RadarrBlocklistEntryUi = RadarrBlocklistEntryUi(
        id = entry.id,
        movieId = entry.movieId,
        title = entry.movie.title?.takeIf { it.isNotEmpty() }
            ?: entry.sourceTitle.orEmpty().ifEmpty { UNKNOWN_TITLE },
        movieYear = entry.movie.year.takeIf { it > 0 },
        sourceTitle = entry.sourceTitle?.takeIf { it.isNotEmpty() },
        date = ArrTime.parse(entry.date),
        relativeDate = ArrTime.parse(entry.date)?.let { ArrTime.relative(it, now) },
        qualityName = entry.quality.quality?.name?.let(Humanize::qualityName),
        protocol = entry.protocol,
        indexer = entry.indexer?.takeIf { it.isNotEmpty() },
        reason = entry.message?.takeIf { it.isNotEmpty() },
        customFormatNames = entry.customFormats.orEmpty()
            .mapNotNull { it.name?.takeIf(String::isNotEmpty) },
        languageNames = entry.languages.orEmpty()
            .mapNotNull { it.name?.takeIf(String::isNotEmpty) },
        queuedOffline = queuedOffline,
    )

    fun presentBlocklist(
        entries: List<BlocklistResource>,
        queuedIds: Set<Int> = emptySet(),
        now: Instant = Instant.now(),
    ): List<RadarrBlocklistEntryUi> = entries
        .sortedWith(compareByDescending<BlocklistResource> { ArrTime.parse(it.date) ?: Instant.EPOCH }
            .thenByDescending { it.id })
        .map { presentBlocklist(it, queuedOffline = it.id in queuedIds, now = now) }

    /**
     * Applies every feed filter at once. Input order is the presenter's
     * date-descending order, not the store's insertion order.
     */
    fun filter(
        events: List<RadarrHistoryEventUi>,
        typeFilter: HistoryEventTypeFilter,
        window: HistoryDateWindow,
        query: String,
        now: Instant,
    ): List<RadarrHistoryEventUi> = events.filter { event ->
        (typeFilter.wireNames.isEmpty() || event.eventType.toString() in typeFilter.wireNames) &&
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
        events: List<RadarrHistoryEventUi>,
        window: HistoryDateWindow,
        endReached: Boolean,
        now: Instant,
    ): Boolean {
        if (endReached) return false
        val duration = window.durationMillis ?: return true
        val oldest = events.minOfOrNull { it.date ?: Instant.EPOCH } ?: return true
        return oldest >= now.minusMillis(duration)
    }

    private fun matchesWindowDate(date: Instant?, window: HistoryDateWindow, now: Instant): Boolean {
        val duration = window.durationMillis ?: return true
        val at = date ?: return false
        return at >= now.minusMillis(duration)
    }

    private fun matchesQueryText(event: RadarrHistoryEventUi, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return event.title.contains(needle, ignoreCase = true) ||
            event.sourceTitle?.contains(needle, ignoreCase = true) == true
    }

    private fun dataValue(event: HistoryResource, key: String): String? =
        event.data?.get(key)?.takeIf { !it.isNullOrEmpty() }

    /**
     * Radarr reports age in the data map as fractional days, whole hours, or
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

    private const val UNKNOWN_TITLE = "—"
}
