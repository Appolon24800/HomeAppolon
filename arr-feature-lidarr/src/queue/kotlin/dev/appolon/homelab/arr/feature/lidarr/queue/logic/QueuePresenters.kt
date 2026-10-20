package dev.appolon.homelab.arr.feature.lidarr.queue.logic

import dev.appolon.homelab.arr.api.lidarr.model.QueueResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueStatusResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackedDownloadState
import dev.appolon.homelab.arr.api.lidarr.model.TrackedDownloadStatus
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.ui.components.QueueRowStatus
import java.util.Locale
import kotlin.math.roundToLong

/** Status slices the queue list offers as filter chips. */
enum class LidarrQueueFilter {
    ALL, DOWNLOADING, IMPORTING, FAILED, UNKNOWN,
}

/** One queue row ready for rendering; no Android or Compose types. */
data class LidarrQueueItemUi(
    val id: Int,
    val title: String,
    val artistId: Int?,
    val artistTitle: String?,
    val albumId: Int?,
    val albumTitle: String?,
    /** Imported-over-total track counts; null when Lidarr reports none. */
    val trackTotal: Int?,
    val trackImported: Int?,
    val status: QueueRowStatus,
    val hasWarning: Boolean,
    val progress: Float?,
    val totalBytes: Long?,
    val remainingBytes: Long?,
    val etaSeconds: Long?,
    val qualityName: String?,
    val customFormatScore: Int,
    val indexer: String?,
    val downloadClient: String?,
    val protocol: dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol,
    val downloadId: String?,
    val outputPath: String?,
    val errorMessage: String?,
    val statusMessages: List<dev.appolon.homelab.arr.api.lidarr.model.TrackedDownloadStatusMessage>,
    val canGrab: Boolean,
    val canRetryImport: Boolean,
    val canManualImport: Boolean,
    val queuedOffline: Boolean = false,
)

/** Counts and aggregate progress driving the header and the shell badge. */
data class LidarrQueueOverview(
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
 * Pure presentation for the Lidarr queue: wire items in, artist/album-level
 * row models and aggregates out. Lidarr embeds the matched artist and album
 * as typed resources and reports per-item track counts instead of episode
 * ids, so the row projection here is album-centric and directly
 * unit-testable per the architecture doc.
 */
object LidarrQueuePresenters {

    /** Download-status wire strings that map to the queued row slice. */
    private val QUEUED_STATUSES = setOf(
        "queued",
        "delay",
        "downloadclientunavailable",
        "download_client_unavailable",
        "unknown",
        "fallback",
    )

    /** Row status follows the tracked state first (it is the truth about importing), then the download status. */
    fun rowStatusOf(item: QueueResource): QueueRowStatus {
        val downloadStatus = item.status?.trim()?.lowercase(Locale.US)
        return when {
            item.trackedDownloadState == TrackedDownloadState.IMPORTING ||
                item.trackedDownloadState == TrackedDownloadState.IMPORT_PENDING -> QueueRowStatus.IMPORTING

            downloadStatus == "failed" ||
                downloadStatus == "error" ||
                item.trackedDownloadState == TrackedDownloadState.DOWNLOAD_FAILED ||
                item.trackedDownloadState == TrackedDownloadState.DOWNLOAD_FAILED_PENDING ||
                item.trackedDownloadState == TrackedDownloadState.IMPORT_FAILED ||
                item.trackedDownloadStatus == TrackedDownloadStatus.ERROR -> QueueRowStatus.FAILED

            downloadStatus == "paused" -> QueueRowStatus.PAUSED
            downloadStatus in QUEUED_STATUSES -> QueueRowStatus.QUEUED

            // A warning flag without a hard failure keeps the underlying
            // in-flight status; the warning surfaces through hasWarning.
            else -> QueueRowStatus.DOWNLOADING
        }
    }

    /** True when the item can be force-grabbed (pending releases only). */
    fun canGrab(item: QueueResource): Boolean {
        val downloadStatus = item.status?.trim()?.lowercase(Locale.US)
        return downloadStatus == "queued" || downloadStatus == "delay"
    }

    /** True when Lidarr can rescan and import the download again. */
    fun canRetryImport(item: QueueResource): Boolean =
        !item.downloadId.isNullOrEmpty() && (
            item.trackedDownloadState == TrackedDownloadState.IMPORT_BLOCKED ||
                item.trackedDownloadState == TrackedDownloadState.IMPORT_FAILED ||
                item.trackedDownloadState == TrackedDownloadState.DOWNLOAD_FAILED ||
                item.trackedDownloadState == TrackedDownloadState.DOWNLOAD_FAILED_PENDING ||
                item.trackedDownloadStatus == TrackedDownloadStatus.ERROR
            )

    /** Interactive import needs an output path to browse. */
    fun canManualImport(item: QueueResource): Boolean = !item.outputPath.isNullOrEmpty()

    /** Matched artist name from the embedded artist resource, when present. */
    fun artistTitleOf(item: QueueResource): String? =
        item.artist?.artistName?.takeIf { it.isNotEmpty() }

    /** Row title: the artist when Lidarr matched one, else the release title. */
    fun titleOf(item: QueueResource): String =
        artistTitleOf(item) ?: item.title.orEmpty().ifEmpty { UNKNOWN_TITLE }

    /** Matched album title from the embedded album resource, when present. */
    fun albumTitleOf(item: QueueResource): String? = item.album?.title?.takeIf { it.isNotEmpty() }

    /** Track totals; null when Lidarr reports no track breakdown for the item. */
    fun trackTotalOf(item: QueueResource): Int? = item.trackFileCount.takeIf { it > 0 }

    /** Tracks already imported out of [trackTotalOf]. */
    fun trackImportedOf(item: QueueResource): Int? =
        if (item.trackFileCount > 0) item.trackHasFileCount.coerceIn(0, item.trackFileCount) else null

    /** Bytes completed fraction; null when the size is unknown or unusable. */
    fun progressOf(item: QueueResource): Float? {
        if (item.size <= 0.0) return null
        val fraction = (item.size - item.sizeleft) / item.size
        return fraction.coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Parses Lidarr's TimeSpan-shaped `timeleft` (`02:03:04`, `1.02:03:04`)
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
    ): LidarrQueueItemUi = LidarrQueueItemUi(
        id = item.id,
        title = titleOf(item),
        artistId = item.artistId,
        artistTitle = artistTitleOf(item),
        albumId = item.albumId,
        albumTitle = albumTitleOf(item),
        trackTotal = trackTotalOf(item),
        trackImported = trackImportedOf(item),
        status = rowStatusOf(item),
        hasWarning = item.trackedDownloadStatus == TrackedDownloadStatus.WARNING,
        progress = progressOf(item),
        totalBytes = if (item.size > 0.0) item.size.roundToLong() else null,
        remainingBytes = if (item.size > 0.0) item.sizeleft.coerceAtLeast(0.0).roundToLong() else null,
        etaSeconds = etaSecondsOf(item),
        qualityName = item.quality?.quality?.name?.let(Humanize::qualityName),
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
    ): List<LidarrQueueItemUi> = items.map { present(it, queuedOffline = it.id in queuedItemIds) }

    /** Applies one filter slice to presented rows. */
    fun filter(
        items: List<LidarrQueueItemUi>,
        filter: LidarrQueueFilter,
    ): List<LidarrQueueItemUi> = when (filter) {
        LidarrQueueFilter.ALL -> items
        LidarrQueueFilter.DOWNLOADING -> items.filter {
            it.status == QueueRowStatus.DOWNLOADING || it.status == QueueRowStatus.PAUSED ||
                it.status == QueueRowStatus.QUEUED
        }

        LidarrQueueFilter.IMPORTING -> items.filter { it.status == QueueRowStatus.IMPORTING }
        LidarrQueueFilter.FAILED -> items.filter { it.status == QueueRowStatus.FAILED }
        LidarrQueueFilter.UNKNOWN -> items.filter { it.artistId == null }
    }

    /**
     * Header and badge aggregates. Counts come from the presented statuses so
     * list and header can never disagree; error and warning flags prefer the
     * QueueStatus endpoint (unknown items folded in) and fall back to
     * scanning the items.
     */
    fun overviewOf(
        items: List<QueueResource>,
        status: QueueStatusResource?,
    ): LidarrQueueOverview {
        val statuses = items.map(::rowStatusOf)
        val warnings = items.any { it.trackedDownloadStatus == TrackedDownloadStatus.WARNING }
        val errors = items.any { it.trackedDownloadStatus == TrackedDownloadStatus.ERROR }
        val totalSize = items.fold(0.0) { sum, item -> sum + item.size.coerceAtLeast(0.0) }
        val remaining = items.fold(0.0) { sum, item -> sum + item.sizeleft.coerceAtLeast(0.0) }
        return LidarrQueueOverview(
            totalCount = items.size,
            unknownCount = items.count { it.artistId == null },
            downloadingCount = statuses.count { it == QueueRowStatus.DOWNLOADING },
            importingCount = statuses.count { it == QueueRowStatus.IMPORTING },
            failedCount = statuses.count { it == QueueRowStatus.FAILED },
            errors = status?.let { it.errors || it.unknownErrors } ?: errors,
            warnings = status?.let { it.warnings || it.unknownWarnings } ?: warnings,
            overallProgress = if (totalSize > 0.0) {
                ((totalSize - remaining) / totalSize).coerceIn(0.0, 1.0).toFloat()
            } else {
                null
            },
        )
    }

    private const val UNKNOWN_TITLE = "—"
}
