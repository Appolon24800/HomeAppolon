package dev.appolon.homelab.arr.feature.sonarr.queue.logic

import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.sonarr.model.QueueResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueStatus
import dev.appolon.homelab.arr.api.sonarr.model.QueueStatusResource
import dev.appolon.homelab.arr.api.sonarr.model.TrackedDownloadState
import dev.appolon.homelab.arr.api.sonarr.model.TrackedDownloadStatus
import dev.appolon.homelab.arr.api.sonarr.model.TrackedDownloadStatusMessage
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.ui.components.QueueRowStatus
import java.util.Locale
import kotlin.math.roundToLong

/** Zero-padded season and episode label Sonarr's web UI shows ("S01E04"). */
fun episodeLabelOf(seasonNumber: Int, episodeNumber: Int): String =
    String.format(Locale.US, "S%02dE%02d", seasonNumber, episodeNumber)

/** Status slices the queue list offers as filter chips. */
enum class SonarrQueueFilter {
    ALL, DOWNLOADING, IMPORTING, FAILED, UNKNOWN,
}

/** One queue row ready for rendering; no Android or Compose types. */
data class SonarrQueueItemUi(
    val id: Int,
    val title: String,
    /** "S02E05" when season and episode are both known; null otherwise. */
    val episodeLabel: String?,
    val episodeTitle: String?,
    val status: QueueRowStatus,
    val hasWarning: Boolean,
    val progress: Float?,
    val totalBytes: Long?,
    val remainingBytes: Long?,
    val etaSeconds: Long?,
    val episodeId: Int?,
    val seriesId: Int?,
    val seriesTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val qualityName: String?,
    val customFormatScore: Int,
    val indexer: String?,
    val downloadClient: String?,
    val protocol: DownloadProtocol,
    val downloadId: String?,
    val outputPath: String?,
    val errorMessage: String?,
    val statusMessages: List<TrackedDownloadStatusMessage>,
    val canGrab: Boolean,
    val canRetryImport: Boolean,
    val canManualImport: Boolean,
    val queuedOffline: Boolean = false,
)

/** Counts and aggregate progress driving the header and the shell badge. */
data class SonarrQueueOverview(
    val totalCount: Int,
    val unknownCount: Int,
    val downloadingCount: Int,
    val importingCount: Int,
    val failedCount: Int,
    val errors: Boolean,
    val warnings: Boolean,
    /** Bytes-weighted completion over items with a known size; null when nothing measures. */
    val overallProgress: Float?,
)

/**
 * Pure presentation for the Sonarr queue: wire items in, episode-level row
 * models and aggregates out. Series and episode arrive as typed DTOs with
 * empty defaults (id = 0, null titles) meaning "unmatched", so the label
 * extraction lives here, free of framework types and directly unit-testable
 * per the architecture doc.
 */
object SonarrQueuePresenters {

    /** Row status follows the tracked state first (it is the truth about importing), then the download status. */
    fun rowStatusOf(item: QueueResource): QueueRowStatus = when {
        item.trackedDownloadState == TrackedDownloadState.IMPORTING ||
            item.trackedDownloadState == TrackedDownloadState.IMPORT_PENDING -> QueueRowStatus.IMPORTING

        item.status == QueueStatus.FAILED ||
            item.trackedDownloadState == TrackedDownloadState.FAILED ||
            item.trackedDownloadState == TrackedDownloadState.FAILED_PENDING ||
            item.trackedDownloadStatus == TrackedDownloadStatus.ERROR -> QueueRowStatus.FAILED

        item.status == QueueStatus.PAUSED -> QueueRowStatus.PAUSED
        item.status == QueueStatus.QUEUED ||
            item.status == QueueStatus.DELAY ||
            item.status == QueueStatus.DOWNLOAD_CLIENT_UNAVAILABLE ||
            item.status == QueueStatus.UNKNOWN ||
            item.status == QueueStatus.FALLBACK -> QueueRowStatus.QUEUED

        // A warning flag without a hard failure keeps the underlying
        // in-flight status; the warning surfaces through hasWarning.
        else -> QueueRowStatus.DOWNLOADING
    }

    /** True when the item can be force-grabbed (pending releases only). */
    fun canGrab(item: QueueResource): Boolean =
        item.status == QueueStatus.QUEUED || item.status == QueueStatus.DELAY

    /** True when Sonarr can rescan and import the download again. */
    fun canRetryImport(item: QueueResource): Boolean =
        !item.downloadId.isNullOrEmpty() && (
            item.trackedDownloadState == TrackedDownloadState.IMPORT_BLOCKED ||
                item.trackedDownloadState == TrackedDownloadState.FAILED ||
                item.trackedDownloadState == TrackedDownloadState.FAILED_PENDING ||
                item.trackedDownloadStatus == TrackedDownloadStatus.ERROR
            )

    /** Interactive import needs an output path to browse. */
    fun canManualImport(item: QueueResource): Boolean = !item.outputPath.isNullOrEmpty()

    /** Matched series title; null when the embedded series is the empty unmatched default. */
    fun seriesTitleOf(item: QueueResource): String? = item.series.title

    /** Row title: the series when Sonarr matched one, else the release title. */
    fun titleOf(item: QueueResource): String =
        seriesTitleOf(item) ?: item.title.orEmpty().ifEmpty { UNKNOWN_TITLE }

    /** Season of the row: the top-level field first, then the embedded episode. */
    fun seasonOf(item: QueueResource): Int? =
        item.seasonNumber?.takeIf { it > 0 } ?: item.episode.seasonNumber?.takeIf { it > 0 }

    /** Episode number from the embedded episode object, when present. */
    fun episodeNumberOf(item: QueueResource): Int? = item.episode.episodeNumber?.takeIf { it > 0 }

    /** Episode title from the embedded episode object, when present. */
    fun episodeTitleOf(item: QueueResource): String? = item.episode.title

    /** "S02E05" when both parts are known; null for unknown-series rows. */
    fun episodeLabelOf(item: QueueResource): String? {
        val season = seasonOf(item) ?: return null
        val episode = episodeNumberOf(item) ?: return null
        return episodeLabelOf(season, episode)
    }

    /** Bytes completed fraction; null when the size is unknown or unusable. */
    fun progressOf(item: QueueResource): Float? {
        if (item.size <= 0.0) return null
        val fraction = (item.size - item.sizeleft) / item.size
        return fraction.coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Parses Sonarr's TimeSpan-shaped `timeleft` (`02:03:04`, `1.02:03:04`)
     * into seconds; null when absent or malformed.
     */
    fun etaSecondsOf(item: QueueResource): Long? {
        val text = item.timeleft?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val daysSplit = text.split('.')
        val days = if (daysSplit.size == 2) daysSplit[0].toLongOrNull() ?: return null else 0L
        val parts = daysSplit.last().split(':')
        if (parts.size != 3) return null
        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val seconds = parts[2].toLongOrNull() ?: return null
        return ((days * 24 + hours) * 3600) + (minutes * 60) + seconds
    }

    fun present(
        item: QueueResource,
        queuedOffline: Boolean = false,
    ): SonarrQueueItemUi = SonarrQueueItemUi(
        id = item.id,
        title = titleOf(item),
        episodeLabel = episodeLabelOf(item),
        episodeTitle = episodeTitleOf(item),
        status = rowStatusOf(item),
        hasWarning = item.trackedDownloadStatus == TrackedDownloadStatus.WARNING,
        progress = progressOf(item),
        totalBytes = if (item.size > 0.0) item.size.roundToLong() else null,
        remainingBytes = if (item.size > 0.0) item.sizeleft.coerceAtLeast(0.0).roundToLong() else null,
        etaSeconds = etaSecondsOf(item),
        episodeId = item.episodeId,
        seriesId = item.seriesId,
        seriesTitle = seriesTitleOf(item),
        seasonNumber = seasonOf(item),
        episodeNumber = episodeNumberOf(item),
        qualityName = item.quality.quality.name?.let(Humanize::qualityName),
        customFormatScore = item.customFormatScore,
        indexer = item.indexer,
        downloadClient = item.downloadClient,
        protocol = item.protocol,
        downloadId = item.downloadId,
        outputPath = item.outputPath,
        errorMessage = item.errorMessage,
        statusMessages = item.statusMessages.orEmpty(),
        canGrab = canGrab(item),
        canRetryImport = canRetryImport(item),
        canManualImport = canManualImport(item),
        queuedOffline = queuedOffline,
    )

    fun present(
        items: List<QueueResource>,
        queuedItemIds: Set<Int> = emptySet(),
    ): List<SonarrQueueItemUi> = items.map { present(it, queuedOffline = it.id in queuedItemIds) }

    /** Applies one filter slice to presented rows. */
    fun filter(
        items: List<SonarrQueueItemUi>,
        filter: SonarrQueueFilter,
    ): List<SonarrQueueItemUi> = when (filter) {
        SonarrQueueFilter.ALL -> items
        SonarrQueueFilter.DOWNLOADING -> items.filter {
            it.status == QueueRowStatus.DOWNLOADING || it.status == QueueRowStatus.PAUSED ||
                it.status == QueueRowStatus.QUEUED
        }

        SonarrQueueFilter.IMPORTING -> items.filter { it.status == QueueRowStatus.IMPORTING }
        SonarrQueueFilter.FAILED -> items.filter { it.status == QueueRowStatus.FAILED }
        SonarrQueueFilter.UNKNOWN -> items.filter { it.seriesId == null }
    }

    /**
     * Header and badge aggregates. Counts come from the presented statuses so
     * list and header can never disagree; error and warning flags prefer the
     * QueueStatus endpoint and fall back to scanning the items.
     */
    fun overviewOf(
        items: List<QueueResource>,
        status: QueueStatusResource?,
    ): SonarrQueueOverview {
        val statuses = items.map(::rowStatusOf)
        val warnings = items.any { it.trackedDownloadStatus == TrackedDownloadStatus.WARNING }
        val errors = items.any { it.trackedDownloadStatus == TrackedDownloadStatus.ERROR }
        val totalSize = items.fold(0.0) { sum, item -> sum + item.size.coerceAtLeast(0.0) }
        val remaining = items.fold(0.0) { sum, item -> sum + item.sizeleft.coerceAtLeast(0.0) }
        return SonarrQueueOverview(
            totalCount = items.size,
            unknownCount = items.count { it.seriesId == null },
            downloadingCount = statuses.count { it == QueueRowStatus.DOWNLOADING },
            importingCount = statuses.count { it == QueueRowStatus.IMPORTING },
            failedCount = statuses.count { it == QueueRowStatus.FAILED },
            errors = status?.errors ?: errors,
            warnings = status?.warnings ?: warnings,
            overallProgress = if (totalSize > 0.0) {
                ((totalSize - remaining) / totalSize).coerceIn(0.0, 1.0).toFloat()
            } else {
                null
            },
        )
    }

    private const val UNKNOWN_TITLE = "—"
}
