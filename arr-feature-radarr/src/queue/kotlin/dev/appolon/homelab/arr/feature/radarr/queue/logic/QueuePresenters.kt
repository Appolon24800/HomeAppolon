package dev.appolon.homelab.arr.feature.radarr.queue.logic

import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.QueueResource
import dev.appolon.homelab.arr.api.radarr.model.QueueStatus
import dev.appolon.homelab.arr.api.radarr.model.QueueStatusResource
import dev.appolon.homelab.arr.api.radarr.model.TrackedDownloadState
import dev.appolon.homelab.arr.api.radarr.model.TrackedDownloadStatus
import dev.appolon.homelab.arr.api.radarr.model.TrackedDownloadStatusMessage
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.ui.components.QueueRowStatus
import kotlin.math.roundToLong

/** Status slices the queue list offers as filter chips. */
enum class RadarrQueueFilter {
    ALL, DOWNLOADING, IMPORTING, FAILED, UNKNOWN,
}

/** One queue row ready for rendering; no Android or Compose types. */
data class RadarrQueueItemUi(
    val id: Int,
    val title: String,
    val status: QueueRowStatus,
    val hasWarning: Boolean,
    val progress: Float?,
    val totalBytes: Long?,
    val remainingBytes: Long?,
    val etaSeconds: Long?,
    val movieId: Int?,
    val movieTitle: String?,
    val movieYear: Int?,
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
data class RadarrQueueOverview(
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
 * Pure presentation for the Radarr queue: wire items in, row models and
 * aggregates out. Kept free of framework types so every rule here is directly
 * unit-testable, per the architecture doc.
 */
object QueuePresenters {

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

    /** True when Radarr can rescan and import the download again. */
    fun canRetryImport(item: QueueResource): Boolean =
        !item.downloadId.isNullOrEmpty() && (
            item.trackedDownloadState == TrackedDownloadState.IMPORT_BLOCKED ||
                item.trackedDownloadState == TrackedDownloadState.FAILED ||
                item.trackedDownloadState == TrackedDownloadState.FAILED_PENDING ||
                item.trackedDownloadStatus == TrackedDownloadStatus.ERROR
            )

    /** Interactive import needs an output path to browse. */
    fun canManualImport(item: QueueResource): Boolean = !item.outputPath.isNullOrEmpty()

    /** Row title: the movie when Radarr matched one, else the release title. */
    fun titleOf(item: QueueResource): String =
        item.movie.title?.takeIf { it.isNotEmpty() } ?: item.title.orEmpty().ifEmpty { UNKNOWN_TITLE }

    /** Bytes completed fraction; null when the size is unknown or unusable. */
    fun progressOf(item: QueueResource): Float? {
        if (item.size <= 0.0) return null
        val fraction = (item.size - item.sizeleft) / item.size
        return fraction.coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Parses Radarr's TimeSpan-shaped `timeleft` (`02:03:04`, `1.02:03:04`)
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
    ): RadarrQueueItemUi = RadarrQueueItemUi(
        id = item.id,
        title = titleOf(item),
        status = rowStatusOf(item),
        hasWarning = item.trackedDownloadStatus == TrackedDownloadStatus.WARNING,
        progress = progressOf(item),
        totalBytes = if (item.size > 0.0) item.size.roundToLong() else null,
        remainingBytes = if (item.size > 0.0) item.sizeleft.coerceAtLeast(0.0).roundToLong() else null,
        etaSeconds = etaSecondsOf(item),
        movieId = item.movieId,
        movieTitle = item.movie.title,
        movieYear = item.movie.year.takeIf { it > 0 },
        qualityName = item.quality.quality?.name?.let(Humanize::qualityName),
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
    ): List<RadarrQueueItemUi> = items.map { present(it, queuedOffline = it.id in queuedItemIds) }

    /** Applies one filter slice to presented rows. */
    fun filter(
        items: List<RadarrQueueItemUi>,
        filter: RadarrQueueFilter,
    ): List<RadarrQueueItemUi> = when (filter) {
        RadarrQueueFilter.ALL -> items
        RadarrQueueFilter.DOWNLOADING -> items.filter {
            it.status == QueueRowStatus.DOWNLOADING || it.status == QueueRowStatus.PAUSED ||
                it.status == QueueRowStatus.QUEUED
        }

        RadarrQueueFilter.IMPORTING -> items.filter { it.status == QueueRowStatus.IMPORTING }
        RadarrQueueFilter.FAILED -> items.filter { it.status == QueueRowStatus.FAILED }
        RadarrQueueFilter.UNKNOWN -> items.filter { it.movieId == null }
    }

    /**
     * Header and badge aggregates. Counts come from the presented statuses so
     * list and header can never disagree; error and warning flags prefer the
     * QueueStatus endpoint and fall back to scanning the items.
     */
    fun overviewOf(
        items: List<QueueResource>,
        status: QueueStatusResource?,
    ): RadarrQueueOverview {
        val statuses = items.map(::rowStatusOf)
        val warnings = items.any { it.trackedDownloadStatus == TrackedDownloadStatus.WARNING }
        val errors = items.any { it.trackedDownloadStatus == TrackedDownloadStatus.ERROR }
        val totalSize = items.fold(0.0) { sum, item -> sum + item.size.coerceAtLeast(0.0) }
        val remaining = items.fold(0.0) { sum, item -> sum + item.sizeleft.coerceAtLeast(0.0) }
        return RadarrQueueOverview(
            totalCount = items.size,
            unknownCount = items.count { it.movieId == null },
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
