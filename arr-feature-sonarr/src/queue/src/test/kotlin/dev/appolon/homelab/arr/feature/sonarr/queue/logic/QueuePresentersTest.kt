package dev.appolon.homelab.arr.feature.sonarr.queue.logic

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueStatus
import dev.appolon.homelab.arr.api.sonarr.model.QueueStatusResource
import dev.appolon.homelab.arr.api.sonarr.model.TrackedDownloadState
import dev.appolon.homelab.arr.api.sonarr.model.TrackedDownloadStatus
import dev.appolon.homelab.arr.ui.components.QueueRowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePresentersTest {

    private fun seriesJson(id: Int, title: String): SeriesResource = SeriesResource(id = id, title = title)

    private fun episodeJson(
        id: Int,
        season: Int,
        number: Int,
        title: String,
    ): EpisodeResource = EpisodeResource(
        id = id,
        seasonNumber = season,
        episodeNumber = number,
        title = title,
    )

    private fun item(
        id: Int = 10,
        status: QueueStatus = QueueStatus.DOWNLOADING,
        state: TrackedDownloadState = TrackedDownloadState.DOWNLOADING,
        trackedStatus: TrackedDownloadStatus = TrackedDownloadStatus.OK,
        size: Double = 0.0,
        sizeleft: Double = 0.0,
        seriesId: Int? = 1,
        seasonNumber: Int? = 2,
        title: String? = "Release.Title.S02E05.2026.1080p",
        series: SeriesResource = seriesJson(1, "Alpha Show"),
        episode: EpisodeResource = episodeJson(50, 2, 5, "Pilot"),
        downloadId: String? = "dl-1",
        outputPath: String? = "/downloads/alpha",
        timeleft: String? = null,
    ) = QueueResource(
        id = id,
        seriesId = seriesId,
        episodeId = if (seriesId != null) 50 else null,
        seasonNumber = seasonNumber,
        series = series,
        episode = episode,
        status = status,
        trackedDownloadStatus = trackedStatus,
        trackedDownloadState = state,
        size = size,
        sizeleft = sizeleft,
        title = title,
        downloadId = downloadId,
        outputPath = outputPath,
        timeleft = timeleft,
    )

    @Test
    fun `importing states map to IMPORTING regardless of download status`() {
        assertEquals(
            QueueRowStatus.IMPORTING,
            SonarrQueuePresenters.rowStatusOf(item(state = TrackedDownloadState.IMPORTING)),
        )
        assertEquals(
            QueueRowStatus.IMPORTING,
            SonarrQueuePresenters.rowStatusOf(
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
            SonarrQueuePresenters.rowStatusOf(item(status = QueueStatus.FAILED)),
        )
        assertEquals(
            QueueRowStatus.FAILED,
            SonarrQueuePresenters.rowStatusOf(item(state = TrackedDownloadState.FAILED)),
        )
        assertEquals(
            QueueRowStatus.FAILED,
            SonarrQueuePresenters.rowStatusOf(item(state = TrackedDownloadState.FAILED_PENDING)),
        )
        assertEquals(
            QueueRowStatus.FAILED,
            SonarrQueuePresenters.rowStatusOf(item(trackedStatus = TrackedDownloadStatus.ERROR)),
        )
    }

    @Test
    fun `download statuses map to their row counterparts`() {
        assertEquals(QueueRowStatus.PAUSED, SonarrQueuePresenters.rowStatusOf(item(status = QueueStatus.PAUSED)))
        assertEquals(
            QueueRowStatus.QUEUED,
            SonarrQueuePresenters.rowStatusOf(item(status = QueueStatus.QUEUED)),
        )
        assertEquals(
            QueueRowStatus.QUEUED,
            SonarrQueuePresenters.rowStatusOf(item(status = QueueStatus.DELAY)),
        )
        assertEquals(
            QueueRowStatus.DOWNLOADING,
            SonarrQueuePresenters.rowStatusOf(item(status = QueueStatus.DOWNLOADING)),
        )
        assertEquals(
            QueueRowStatus.DOWNLOADING,
            SonarrQueuePresenters.rowStatusOf(item(status = QueueStatus.WARNING)),
        )
        assertEquals(
            QueueRowStatus.DOWNLOADING,
            SonarrQueuePresenters.rowStatusOf(item(status = QueueStatus.COMPLETED)),
        )
    }

    @Test
    fun `title prefers the series and falls back to the release title`() {
        assertEquals("Alpha Show", SonarrQueuePresenters.titleOf(item()))
        val unmatched = item(
            series = SeriesResource(),
            seriesId = null,
            title = "Release.Title.S02E05.2026.1080p",
        )
        assertEquals("Release.Title.S02E05.2026.1080p", SonarrQueuePresenters.titleOf(unmatched))
    }

    @Test
    fun `episode labels format SxxEyy and fall back to the episode object`() {
        assertEquals("S02E05", SonarrQueuePresenters.episodeLabelOf(item()))
        // Top-level season missing: the embedded episode supplies it.
        assertEquals(
            "S02E05",
            SonarrQueuePresenters.episodeLabelOf(item(seasonNumber = null)),
        )
        assertNull(SonarrQueuePresenters.episodeLabelOf(item(episode = EpisodeResource())))
        assertEquals("Pilot", SonarrQueuePresenters.episodeTitleOf(item()))
        assertEquals(2, SonarrQueuePresenters.seasonOf(item()))
        assertEquals(5, SonarrQueuePresenters.episodeNumberOf(item()))
    }

    @Test
    fun `progress is clamped and null without a size`() {
        assertEquals(0.75f, SonarrQueuePresenters.progressOf(item(size = 8.0, sizeleft = 2.0)))
        assertEquals(1.0f, SonarrQueuePresenters.progressOf(item(size = 8.0, sizeleft = -1.0)))
        assertEquals(0.0f, SonarrQueuePresenters.progressOf(item(size = 8.0, sizeleft = 12.0)))
        assertNull(SonarrQueuePresenters.progressOf(item(size = 0.0, sizeleft = 0.0)))
    }

    @Test
    fun `eta parses timespan shapes and rejects junk`() {
        assertEquals(7384L, SonarrQueuePresenters.etaSecondsOf(item(timeleft = "02:03:04")))
        assertEquals(93784L, SonarrQueuePresenters.etaSecondsOf(item(timeleft = "1.02:03:04")))
        assertNull(SonarrQueuePresenters.etaSecondsOf(item(timeleft = "soon")))
        assertNull(SonarrQueuePresenters.etaSecondsOf(item(timeleft = null)))
    }

    @Test
    fun `action availability follows state`() {
        assertTrue(SonarrQueuePresenters.canGrab(item(status = QueueStatus.QUEUED)))
        assertFalse(SonarrQueuePresenters.canGrab(item(status = QueueStatus.DOWNLOADING)))

        val blocked = item(
            state = TrackedDownloadState.IMPORT_BLOCKED,
            trackedStatus = TrackedDownloadStatus.ERROR,
        )
        assertTrue(SonarrQueuePresenters.canRetryImport(blocked))
        assertFalse(SonarrQueuePresenters.canRetryImport(blocked.copy(downloadId = null)))
        assertTrue(SonarrQueuePresenters.canManualImport(item(outputPath = "/x")))
        assertFalse(SonarrQueuePresenters.canManualImport(item(outputPath = null)))
    }

    @Test
    fun `present marks queued rows without touching the others`() {
        val rows = SonarrQueuePresenters.present(
            listOf(item(id = 1), item(id = 2), item(id = 3)),
            queuedItemIds = setOf(2),
        )
        assertEquals(listOf(false, true, false), rows.map { it.queuedOffline })
    }

    @Test
    fun `present carries episode fields onto the row model`() {
        val row = SonarrQueuePresenters.present(item(id = 7))

        assertEquals("Alpha Show", row.title)
        assertEquals("S02E05", row.episodeLabel)
        assertEquals("Pilot", row.episodeTitle)
        assertEquals(50, row.episodeId)
        assertEquals(1, row.seriesId)
        assertEquals(2, row.seasonNumber)
        assertEquals(5, row.episodeNumber)
    }

    @Test
    fun `filters slice by presented status and unknown items`() {
        val downloading = item(id = 1, status = QueueStatus.DOWNLOADING)
        val queued = item(id = 2, status = QueueStatus.QUEUED)
        val importing = item(id = 3, state = TrackedDownloadState.IMPORTING)
        val failed = item(id = 4, status = QueueStatus.FAILED)
        val unknown = item(
            id = 5,
            seriesId = null,
            series = SeriesResource(),
            episode = EpisodeResource(),
            seasonNumber = null,
        )
        val presented = SonarrQueuePresenters.present(
            listOf(downloading, queued, importing, failed, unknown),
        )

        assertEquals(5, SonarrQueuePresenters.filter(presented, SonarrQueueFilter.ALL).size)
        // Unknown items still download, so they ride along in the downloading slice.
        assertEquals(
            listOf(1, 2, 5),
            SonarrQueuePresenters.filter(presented, SonarrQueueFilter.DOWNLOADING).map { it.id },
        )
        assertEquals(
            listOf(3),
            SonarrQueuePresenters.filter(presented, SonarrQueueFilter.IMPORTING).map { it.id },
        )
        assertEquals(listOf(4), SonarrQueuePresenters.filter(presented, SonarrQueueFilter.FAILED).map { it.id })
        assertEquals(listOf(5), SonarrQueuePresenters.filter(presented, SonarrQueueFilter.UNKNOWN).map { it.id })
    }

    @Test
    fun `overview counts statuses, prefers endpoint flags, and weighs progress by bytes`() {
        val items = listOf(
            item(size = 8.0, sizeleft = 4.0),
            item(size = 2.0, sizeleft = 0.0, state = TrackedDownloadState.IMPORTING),
            item(status = QueueStatus.FAILED, size = 0.0),
        )
        val status = QueueStatusResource(totalCount = 3, count = 3, errors = true, warnings = true)
        val overview = SonarrQueuePresenters.overviewOf(items, status)

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
        val overview = SonarrQueuePresenters.overviewOf(listOf(warning, error), null)

        assertTrue(overview.warnings)
        assertTrue(overview.errors)
        assertNull(overview.overallProgress)
    }
}
