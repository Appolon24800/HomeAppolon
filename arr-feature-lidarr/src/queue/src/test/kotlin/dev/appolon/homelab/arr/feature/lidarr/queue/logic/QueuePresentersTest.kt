package dev.appolon.homelab.arr.feature.lidarr.queue.logic

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueStatusResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackedDownloadState
import dev.appolon.homelab.arr.api.lidarr.model.TrackedDownloadStatus
import dev.appolon.homelab.arr.ui.components.QueueRowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePresentersTest {

    private fun item(
        id: Int = 1,
        artistId: Int? = id,
        status: String? = "downloading",
        state: TrackedDownloadState = TrackedDownloadState.DOWNLOADING,
        trackedStatus: TrackedDownloadStatus = TrackedDownloadStatus.OK,
        size: Double = 10.0,
        sizeleft: Double = 2.0,
        timeleft: String? = "00:01:40",
        downloadId: String? = "dl-$id",
        outputPath: String? = "/music/artist",
        trackFileCount: Int = 0,
        trackHasFileCount: Int = 0,
    ) = QueueResource(
        id = id,
        artistId = artistId,
        albumId = if (artistId != null) id + 50 else null,
        artist = if (artistId != null) {
            ArtistResource(id = artistId, artistName = "Artist $artistId")
        } else {
            null
        },
        album = if (artistId != null) AlbumResource(id = id + 50, title = "Album $id") else null,
        title = "Release.$id",
        status = status,
        trackedDownloadState = state,
        trackedDownloadStatus = trackedStatus,
        size = size,
        sizeleft = sizeleft,
        timeleft = timeleft,
        downloadId = downloadId,
        outputPath = outputPath,
        trackFileCount = trackFileCount,
        trackHasFileCount = trackHasFileCount,
    )

    @Test
    fun `row status follows the tracked state first, then the download status string`() {
        assertEquals(QueueRowStatus.IMPORTING, LidarrQueuePresenters.rowStatusOf(item(state = TrackedDownloadState.IMPORTING)))
        assertEquals(
            QueueRowStatus.IMPORTING,
            LidarrQueuePresenters.rowStatusOf(item(state = TrackedDownloadState.IMPORT_PENDING)),
        )
        assertEquals(QueueRowStatus.FAILED, LidarrQueuePresenters.rowStatusOf(item(status = "failed")))
        assertEquals(
            QueueRowStatus.FAILED,
            LidarrQueuePresenters.rowStatusOf(item(state = TrackedDownloadState.IMPORT_FAILED)),
        )
        assertEquals(
            QueueRowStatus.FAILED,
            LidarrQueuePresenters.rowStatusOf(item(trackedStatus = TrackedDownloadStatus.ERROR)),
        )
        assertEquals(QueueRowStatus.PAUSED, LidarrQueuePresenters.rowStatusOf(item(status = "paused")))
        assertEquals(QueueRowStatus.QUEUED, LidarrQueuePresenters.rowStatusOf(item(status = "queued")))
        assertEquals(
            QueueRowStatus.QUEUED,
            LidarrQueuePresenters.rowStatusOf(item(status = "downloadClientUnavailable")),
        )
        assertEquals(QueueRowStatus.DOWNLOADING, LidarrQueuePresenters.rowStatusOf(item(status = "downloading")))
        assertEquals(QueueRowStatus.DOWNLOADING, LidarrQueuePresenters.rowStatusOf(item(status = null)))
    }

    @Test
    fun `title prefers the matched artist and falls back to the release title`() {
        assertEquals("Artist 1", LidarrQueuePresenters.titleOf(item()))
        assertEquals("Release.2", LidarrQueuePresenters.titleOf(item(id = 2, artistId = null)))
        assertEquals("—", LidarrQueuePresenters.titleOf(item(id = 3, artistId = null).copy(title = null)))
    }

    @Test
    fun `album and track projections carry the album-centric row semantics`() {
        val presented = LidarrQueuePresenters.present(item(trackFileCount = 12, trackHasFileCount = 3))
        assertEquals("Album 1", presented.albumTitle)
        assertEquals(12, presented.trackTotal)
        assertEquals(3, presented.trackImported)
        assertEquals("Artist 1", presented.artistTitle)
        assertEquals(1, presented.artistId)
        assertEquals(51, presented.albumId)

        val unknown = LidarrQueuePresenters.present(item(artistId = null))
        assertNull(unknown.trackTotal)
        assertNull(unknown.trackImported)
        assertNull(unknown.albumTitle)
    }

    @Test
    fun `canGrab is limited to pending releases`() {
        assertTrue(LidarrQueuePresenters.canGrab(item(status = "queued")))
        assertTrue(LidarrQueuePresenters.canGrab(item(status = "delay")))
        assertFalse(LidarrQueuePresenters.canGrab(item(status = "downloading")))
        assertFalse(LidarrQueuePresenters.canGrab(item(status = null)))
    }

    @Test
    fun `canRetryImport needs a download id and a failed or blocked state`() {
        assertTrue(
            LidarrQueuePresenters.canRetryImport(item(state = TrackedDownloadState.IMPORT_BLOCKED)),
        )
        assertTrue(
            LidarrQueuePresenters.canRetryImport(item(state = TrackedDownloadState.DOWNLOAD_FAILED)),
        )
        assertFalse(
            LidarrQueuePresenters.canRetryImport(
                item(state = TrackedDownloadState.IMPORT_BLOCKED, downloadId = null),
            ),
        )
        assertFalse(LidarrQueuePresenters.canRetryImport(item(state = TrackedDownloadState.DOWNLOADING)))
    }

    @Test
    fun `canManualImport requires an output path`() {
        assertTrue(LidarrQueuePresenters.canManualImport(item()))
        assertFalse(LidarrQueuePresenters.canManualImport(item(outputPath = null)))
    }

    @Test
    fun `eta parses Lidarr's TimeSpan shapes`() {
        assertEquals(100L, LidarrQueuePresenters.etaSecondsOf(item(timeleft = "00:01:40")))
        assertEquals(93_784L, LidarrQueuePresenters.etaSecondsOf(item(timeleft = "1.02:03:04")))
        assertNull(LidarrQueuePresenters.etaSecondsOf(item(timeleft = null)))
        assertNull(LidarrQueuePresenters.etaSecondsOf(item(timeleft = "soon")))
    }

    @Test
    fun `progress is bounded and null without a usable size`() {
        assertEquals(0.8f, LidarrQueuePresenters.progressOf(item(size = 10.0, sizeleft = 2.0))!!)
        assertEquals(1.0f, LidarrQueuePresenters.progressOf(item(size = 10.0, sizeleft = -5.0))!!)
        assertNull(LidarrQueuePresenters.progressOf(item(size = 0.0, sizeleft = 0.0)))
    }

    @Test
    fun `present stamps the queued offline flag from the pending ids`() {
        val rows = LidarrQueuePresenters.present(listOf(item(1), item(2)), queuedItemIds = setOf(2))
        assertFalse(rows[0].queuedOffline)
        assertTrue(rows[1].queuedOffline)
    }

    @Test
    fun `filter slices follow the row statuses and unknown artists`() {
        val rows = listOf(
            LidarrQueuePresenters.present(item(1)),
            LidarrQueuePresenters.present(item(2, status = "paused")),
            LidarrQueuePresenters.present(item(3, state = TrackedDownloadState.IMPORTING)),
            LidarrQueuePresenters.present(item(4, status = "failed")),
            LidarrQueuePresenters.present(item(5, artistId = null, status = "queued")),
        )
        assertEquals(5, LidarrQueuePresenters.filter(rows, LidarrQueueFilter.ALL).size)
        // The downloading slice keeps paused and queued rows, mirroring Sonarr's chips.
        assertEquals(
            listOf(1, 2, 5),
            LidarrQueuePresenters.filter(rows, LidarrQueueFilter.DOWNLOADING).map { it.id },
        )
        assertEquals(listOf(3), LidarrQueuePresenters.filter(rows, LidarrQueueFilter.IMPORTING).map { it.id })
        assertEquals(listOf(4), LidarrQueuePresenters.filter(rows, LidarrQueueFilter.FAILED).map { it.id })
        assertEquals(listOf(5), LidarrQueuePresenters.filter(rows, LidarrQueueFilter.UNKNOWN).map { it.id })
    }

    @Test
    fun `overview counts statuses, unknowns, and bytes-weighted progress`() {
        val items = listOf(
            item(1, size = 100.0, sizeleft = 0.0),
            item(2, status = "failed", size = 100.0, sizeleft = 100.0),
            item(3, state = TrackedDownloadState.IMPORTING, size = 100.0, sizeleft = 100.0),
            item(4, artistId = null, status = "queued", trackedStatus = TrackedDownloadStatus.WARNING, size = 100.0, sizeleft = 100.0),
        )

        val overview = LidarrQueuePresenters.overviewOf(items, status = null)

        assertEquals(4, overview.totalCount)
        assertEquals(1, overview.unknownCount)
        assertEquals(1, overview.downloadingCount)
        assertEquals(1, overview.importingCount)
        assertEquals(1, overview.failedCount)
        assertEquals(0.25f, overview.overallProgress)
        assertTrue(overview.warnings)
    }

    @Test
    fun `overview prefers the status endpoint and folds unknown flags in`() {
        val items = listOf(item(1, trackedStatus = TrackedDownloadStatus.OK))

        val clean = LidarrQueuePresenters.overviewOf(
            items,
            QueueStatusResource(errors = false, warnings = false),
        )
        assertFalse(clean.errors)
        assertFalse(clean.warnings)

        val unknownError = LidarrQueuePresenters.overviewOf(
            items,
            QueueStatusResource(unknownErrors = true),
        )
        assertTrue(unknownError.errors)
    }

    @Test
    fun `overview progress is null when nothing measures`() {
        val overview = LidarrQueuePresenters.overviewOf(listOf(item(1, size = 0.0)), null)
        assertNull(overview.overallProgress)
    }
}
