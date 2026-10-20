package dev.appolon.homelab.arr.feature.sonarr.activity.logic

import dev.appolon.homelab.arr.api.sonarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeHistoryEventType
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.Humanize
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Event-type slices the history feed offers as filter chips. */
enum class HistoryEventTypeFilter(val wireNames: Set<String>) {
    ALL(emptySet()),

    /** Grabbed from an indexer. */
    GRABBED(setOf("grabbed")),

    /** Both import variants: download folder and series folder. */
    IMPORTED(setOf("downloadFolderImported", "seriesFolderImported")),

    /** Download failed or ignored by the user. */
    FAILED(setOf("downloadFailed", "downloadIgnored")),

    /** An episode file was deleted. */
    DELETED(setOf("episodeFileDeleted")),

    /** An episode file was renamed. */
    RENAMED(setOf("episodeFileRenamed")),
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
data class SonarrHistoryEventUi(
    val id: Int,
    val eventType: EpisodeHistoryEventType,
    val episodeId: Int,
    val seriesId: Int,
    /** Row title: the series when Sonarr matched one, else the release title. */
    val title: String,
    val seriesYear: Int?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    /** "S01E04" when season and episode are both known; null otherwise. */
    val episodeLabel: String?,
    val episodeTitle: String?,
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
    /** Failed downloads with a download id can retry through manual import. */
    val failedImport: Boolean,
    val queuedOffline: Boolean = false,
)

/** One blocklist entry ready for rendering; no Android or Compose types. */
data class SonarrBlocklistEntryUi(
    val id: Int,
    val seriesId: Int,
    val title: String,
    val seriesYear: Int?,
    val sourceTitle: String?,
    val date: Instant?,
    val relativeDate: String?,
    val qualityName: String?,
    val protocol: DownloadProtocol,
    val indexer: String?,
    /** Sonarr's reason for the rejection (the `message` column). */
    val reason: String?,
    val customFormatNames: List<String>,
    val languageNames: List<String>,
    val queuedOffline: Boolean = false,
)

/**
 * Pure presentation for the Sonarr activity screens: wire resources in,
 * episode-granular row models and filter rules out. The committed DTOs embed
 * series and episode as raw JSON objects, so the label extraction lives
 * here, free of framework types and directly unit-testable per the
 * architecture doc.
 */
object ActivityPresenters {

    /** Zero-padded season and episode label Sonarr's web UI shows ("S01E04"). */
    fun episodeLabelOf(seasonNumber: Int, episodeNumber: Int): String =
        String.format(Locale.US, "S%02dE%02d", seasonNumber, episodeNumber)

    /** Matched series title from the embedded series object, when present. */
    fun seriesTitleOf(event: HistoryResource): String? = event.series.stringOf("title")

    /** Row title: the series when Sonarr matched one, else the release title. */
    fun titleOf(event: HistoryResource): String =
        seriesTitleOf(event) ?: event.sourceTitle.orEmpty().ifEmpty { UNKNOWN_TITLE }

    fun present(
        event: HistoryResource,
        queuedOffline: Boolean = false,
        now: Instant = Instant.now(),
    ): SonarrHistoryEventUi = SonarrHistoryEventUi(
        id = event.id,
        eventType = event.eventType,
        episodeId = event.episodeId,
        seriesId = event.seriesId,
        title = titleOf(event),
        seriesYear = event.series.intOf("year")?.takeIf { it > 0 },
        seasonNumber = event.episode.intOf("seasonNumber"),
        episodeNumber = event.episode.intOf("episodeNumber"),
        episodeLabel = episodeLabelOf(event),
        episodeTitle = event.episode.stringOf("title"),
        sourceTitle = event.sourceTitle?.takeIf { it.isNotEmpty() },
        date = ArrTime.parse(event.date),
        relativeDate = ArrTime.parse(event.date)?.let { ArrTime.relative(it, now) },
        fullDate = ArrTime.parse(event.date)?.let { ArrTime.dateTimeFull(it) },
        qualityName = event.quality.quality.name?.let(Humanize::qualityName),
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
        canMarkFailed = event.eventType == EpisodeHistoryEventType.GRABBED && event.id > 0,
        failedImport = event.eventType == EpisodeHistoryEventType.DOWNLOAD_FAILED &&
            !event.downloadId.isNullOrEmpty(),
        queuedOffline = queuedOffline,
    )

    fun present(
        events: List<HistoryResource>,
        queuedIds: Set<Int> = emptySet(),
        now: Instant = Instant.now(),
    ): List<SonarrHistoryEventUi> = events
        .sortedWith(compareByDescending<HistoryResource> { ArrTime.parse(it.date) ?: Instant.EPOCH }
            .thenByDescending { it.id })
        .map { present(it, queuedOffline = it.id in queuedIds, now = now) }

    fun presentBlocklist(
        entry: BlocklistResource,
        queuedOffline: Boolean = false,
        now: Instant = Instant.now(),
    ): SonarrBlocklistEntryUi = SonarrBlocklistEntryUi(
        id = entry.id,
        seriesId = entry.seriesId,
        title = entry.series.stringOf("title")
            ?: entry.sourceTitle.orEmpty().ifEmpty { UNKNOWN_TITLE },
        seriesYear = entry.series.intOf("year")?.takeIf { it > 0 },
        sourceTitle = entry.sourceTitle?.takeIf { it.isNotEmpty() },
        date = ArrTime.parse(entry.date),
        relativeDate = ArrTime.parse(entry.date)?.let { ArrTime.relative(it, now) },
        qualityName = entry.quality.quality.name?.let(Humanize::qualityName),
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
    ): List<SonarrBlocklistEntryUi> = entries
        .sortedWith(compareByDescending<BlocklistResource> { ArrTime.parse(it.date) ?: Instant.EPOCH }
            .thenByDescending { it.id })
        .map { presentBlocklist(it, queuedOffline = it.id in queuedIds, now = now) }

    /**
     * Applies every feed filter at once. Input order is the presenter's
     * date-descending order, not the store's insertion order.
     */
    fun filter(
        events: List<SonarrHistoryEventUi>,
        typeFilter: HistoryEventTypeFilter,
        window: HistoryDateWindow,
        query: String,
        now: Instant,
    ): List<SonarrHistoryEventUi> = events.filter { event ->
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
        events: List<SonarrHistoryEventUi>,
        window: HistoryDateWindow,
        endReached: Boolean,
        now: Instant,
    ): Boolean {
        if (endReached) return false
        val duration = window.durationMillis ?: return true
        val oldest = events.minOfOrNull { it.date ?: Instant.EPOCH } ?: return true
        return oldest >= now.minusMillis(duration)
    }

    /** "S02E05" when the embedded episode object carries both parts; null otherwise. */
    private fun episodeLabelOf(event: HistoryResource): String? {
        val season = event.episode.intOf("seasonNumber") ?: return null
        val episode = event.episode.intOf("episodeNumber") ?: return null
        return episodeLabelOf(season, episode)
    }

    private fun matchesWindowDate(date: Instant?, window: HistoryDateWindow, now: Instant): Boolean {
        val duration = window.durationMillis ?: return true
        val at = date ?: return false
        return at >= now.minusMillis(duration)
    }

    /** The series query matches the series title, the episode title, or the release name. */
    private fun matchesQueryText(event: SonarrHistoryEventUi, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return event.title.contains(needle, ignoreCase = true) ||
            event.episodeTitle?.contains(needle, ignoreCase = true) == true ||
            event.sourceTitle?.contains(needle, ignoreCase = true) == true
    }

    private fun dataValue(event: HistoryResource, key: String): String? =
        event.data?.get(key)?.takeIf { !it.isNullOrEmpty() }

    /**
     * Sonarr reports age in the data map as fractional days, whole hours, or
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

    private fun JsonObject.stringOf(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

    private fun JsonObject.intOf(key: String): Int? = when (val element = this[key]) {
        is JsonPrimitive -> element.content.toIntOrNull()
        else -> null
    }

    private const val UNKNOWN_TITLE = "—"
}
