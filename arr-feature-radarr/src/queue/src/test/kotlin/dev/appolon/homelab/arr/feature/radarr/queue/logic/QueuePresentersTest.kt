package dev.appolon.homelab.arr.feature.radarr.queue.logic

import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QueueResource
import dev.appolon.homelab.arr.api.radarr.model.QueueStatus
import dev.appolon.homelab.arr.api.radarr.model.QueueStatusResource
import dev.appolon.homelab.arr.api.radarr.model.TrackedDownloadState
import dev.appolon.homelab.arr.api.radarr.model.TrackedDownloadStatus
import dev.appolon.homelab.arr.ui.components.QueueRowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePresentersTest {

    private fun item(
        id: Int = 10,
        status: QueueStatus = QueueStatus.DOWNLOADING,
        state: TrackedDownloadState = TrackedDownloadState.DOWNLOADING,
        trackedStatus: TrackedDownloadStatus = TrackedDownloadStatus.OK,
        size: Double = 0.0,
        sizeleft: Double = 0.0,
        movieId: Int? = 1,
        title: String? = "Release.Title.2026.1080p",
        movie: MovieResource = MovieResource(id = 1, title = "Alpha Movie"),
        downloadId: String? = "dl-1",
        outputPath: String? = "/downloads/alpha",
        timeleft: String? = null,
    ) = QueueResource(
        id = id,
        status = status,
        trackedDownloadState = state,
        trackedDownloadStatus = trackedStatus,
        size = size,
        sizeleft = sizeleft,
        movieId = movieId,
        movie = movie,
        title = title,
        downloadId = downloadId,
        outputPath = outputPath,
        timeleft = timeleft,
    )

    @Test
    fun `importing states map to IMPORTING regardless of download status`() {
        assertEquals(
            QueueRowStatus.IMPORTING,
            QueuePresenters.rowStatusOf(item(state = TrackedDownloadState.IMPORTING)),
        )
        assertEquals(
            QueueRowStatus.IMPORTING,
            QueuePresenters.rowStatusOf(
                item(
                    state = TrackedDownloadState.IMPORT_PENDING,
                    status = QueueStatus.COMPLETED,
                ),
            ),
        )
    }

    @Test
    fun `failure signals map to FAILED`() {
        assertEquals(
            QueueRowStatus.FAILED,
            QueuePresenters.rowStatusOf(item(status = QueueStatus.FAILED)),
        )
        assertEquals(
            QueueRowStatus.FAILED,
            QueuePresenters.rowStatusOf(item(state = TrackedDownloadState.FAILED)),
        )
        assertEquals(
            QueueRowStatus.FAILED,
            QueuePresenters.rowStatusOf(item(state = TrackedDownloadState.FAILED_PENDING)),
        )
        assertEquals(
            QueueRowStatus.FAILED,
            QueuePresenters.rowStatusOf(item(trackedStatus = TrackedDownloadStatus.ERROR)),
        )
    }

    @Test
    fun `download statuses map to their row counterparts`() {
        assertEquals(QueueRowStatus.PAUSED, QueuePresenters.rowStatusOf(item(status = QueueStatus.PAUSED)))
        assertEquals(
            QueueRowStatus.QUEUED,
            QueuePresenters.rowStatusOf(item(status = QueueStatus.QUEUED)),
        )
        assertEquals(
            QueueRowStatus.QUEUED,
            QueuePresenters.rowStatusOf(item(status = QueueStatus.DELAY)),
        )
        assertEquals(
            QueueRowStatus.DOWNLOADING,
            QueuePresenters.rowStatusOf(item(status = QueueStatus.DOWNLOADING)),
        )
        assertEquals(
            QueueRowStatus.DOWNLOADING,
            QueuePresenters.rowStatusOf(item(status = QueueStatus.WARNING)),
        )
        assertEquals(
            QueueRowStatus.DOWNLOADING,
            QueuePresenters.rowStatusOf(item(status = QueueStatus.COMPLETED)),
        )
    }

    @Test
    fun `title prefers the movie and falls back to the release title`() {
        assertEquals("Alpha Movie", QueuePresenters.titleOf(item()))
        val unmatched = item(movie = MovieResource(id = 0), movieId = null)
        assertEquals("Release.Title.2026.1080p", QueuePresenters.titleOf(unmatched))
    }

    @Test
    fun `progress is clamped and null without a size`() {
        assertEquals(0.75f, QueuePresenters.progressOf(item(size = 8.0, sizeleft = 2.0)))
        assertEquals(1.0f, QueuePresenters.progressOf(item(size = 8.0, sizeleft = -1.0)))
        assertEquals(0.0f, QueuePresenters.progressOf(item(size = 8.0, sizeleft = 12.0)))
        assertNull(QueuePresenters.progressOf(item(size = 0.0, sizeleft = 0.0)))
    }

    @Test
    fun `eta parses timespan shapes and rejects junk`() {
        assertEquals(7384L, QueuePresenters.etaSecondsOf(item(timeleft = "02:03:04")))
        assertEquals(93784L, QueuePresenters.etaSecondsOf(item(timeleft = "1.02:03:04")))
        assertNull(QueuePresenters.etaSecondsOf(item(timeleft = "soon")))
        assertNull(QueuePresenters.etaSecondsOf(item(timeleft = null)))
    }

    @Test
    fun `action availability follows state`() {
        assertTrue(QueuePresenters.canGrab(item(status = QueueStatus.QUEUED)))
        assertFalse(QueuePresenters.canGrab(item(status = QueueStatus.DOWNLOADING)))

        val blocked = item(
            state = TrackedDownloadState.IMPORT_BLOCKED,
            trackedStatus = TrackedDownloadStatus.ERROR,
        )
        assertTrue(QueuePresenters.canRetryImport(blocked))
        assertFalse(QueuePresenters.canRetryImport(blocked.copy(downloadId = null)))
        assertTrue(QueuePresenters.canManualImport(item(outputPath = "/x")))
        assertFalse(QueuePresenters.canManualImport(item(outputPath = null)))
    }

    @Test
    fun `present marks queued rows without touching the others`() {
        val rows = QueuePresenters.present(
            listOf(item(id = 1), item(id = 2), item(id = 3)),
            queuedItemIds = setOf(2),
        )
        assertEquals(listOf(false, true, false), rows.map { it.queuedOffline })
    }

    @Test
    fun `filters slice by presented status and unknown items`() {
        val downloading = item(id = 1, status = QueueStatus.DOWNLOADING)
        val queued = item(id = 2, status = QueueStatus.QUEUED)
        val importing = item(id = 3, state = TrackedDownloadState.IMPORTING)
        val failed = item(id = 4, status = QueueStatus.FAILED)
        val unknown = item(id = 5, movieId = null, movie = MovieResource(id = 0))
        val presented = QueuePresenters.present(
            listOf(downloading, queued, importing, failed, unknown),
        )

        assertEquals(5, QueuePresenters.filter(presented, RadarrQueueFilter.ALL).size)
        // Unknown items still download, so they ride along in the downloading slice.
        assertEquals(
            listOf(1, 2, 5),
            QueuePresenters.filter(presented, RadarrQueueFilter.DOWNLOADING).map { it.id },
        )
        assertEquals(
            listOf(3),
            QueuePresenters.filter(presented, RadarrQueueFilter.IMPORTING).map { it.id },
        )
        assertEquals(listOf(4), QueuePresenters.filter(presented, RadarrQueueFilter.FAILED).map { it.id })
        assertEquals(listOf(5), QueuePresenters.filter(presented, RadarrQueueFilter.UNKNOWN).map { it.id })
    }

    @Test
    fun `overview counts statuses, prefers endpoint flags, and weighs progress by bytes`() {
        val items = listOf(
            item(size = 8.0, sizeleft = 4.0),
            item(size = 2.0, sizeleft = 0.0, state = TrackedDownloadState.IMPORTING),
            item(status = QueueStatus.FAILED, size = 0.0),
        )
        val status = QueueStatusResource(totalCount = 3, count = 3, errors = true, warnings = true)
        val overview = QueuePresenters.overviewOf(items, status)

        assertEquals(3, overview.totalCount)
        assertEquals(1, overview.importingCount)
        assertEquals(1, overview.failedCount)
        assertEquals(1, overview.downloadingCount)
        assertTrue(overview.errors)
        assertTrue(overview.warnings)
        assertEquals(0.6f, overview.overallProgress)
    }

    @Test
    fun `overview falls back to item flags and null progress without sizes`() {
        val warning = item(trackedStatus = TrackedDownloadStatus.WARNING)
        val error = item(trackedStatus = TrackedDownloadStatus.ERROR)
        val overview = QueuePresenters.overviewOf(listOf(warning, error), null)

        assertTrue(overview.warnings)
        assertTrue(overview.errors)
        assertNull(overview.overallProgress)
    }
}
