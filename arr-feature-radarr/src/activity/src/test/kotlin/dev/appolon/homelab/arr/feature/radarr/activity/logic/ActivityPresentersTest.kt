package dev.appolon.homelab.arr.feature.radarr.activity.logic

import dev.appolon.homelab.arr.api.radarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.HistoryResource
import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.MovieHistoryEventType
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityModel
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityPresentersTest {

    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")

    private fun event(
        id: Int,
        eventType: MovieHistoryEventType,
        date: String = "2026-10-20T10:00:00Z",
        title: String? = "Movie $id",
        sourceTitle: String? = "Release.$id",
        downloadId: String? = null,
        data: Map<String, String?>? = null,
    ) = HistoryResource(
        id = id,
        movieId = id,
        sourceTitle = sourceTitle,
        quality = QualityModel(quality = Quality(id = 1, name = "WEBDL-1080p")),
        date = date,
        downloadId = downloadId,
        eventType = eventType,
        data = data,
        movie = MovieResource(id = id, title = title, year = 2020),
    )

    @Test
    fun `present pulls detail fields out of the data map`() {
        val presented = ActivityPresenters.present(
            event(
                1,
                MovieHistoryEventType.GRABBED,
                downloadId = "dl-1",
                data = mapOf(
                    "indexer" to "Indexer A",
                    "downloadClient" to "sabnzbd",
                    "source" to "web",
                    "age" to "1.5",
                    "size" to "2048000",
                    "releaseGroup" to "GROUP",
                    "message" to "manual grab",
                ),
            ),
            now = now,
        )

        assertEquals("Movie 1", presented.title)
        assertEquals("WEB-DL 1080p", presented.qualityName)
        assertEquals("Indexer A", presented.indexer)
        assertEquals("sabnzbd", presented.downloadClient)
        assertEquals("web", presented.source)
        assertEquals("36h", presented.ageText)
        assertEquals("2.0 MB", presented.sizeText)
        assertEquals("GROUP", presented.releaseGroup)
        assertEquals("manual grab", presented.message)
        assertEquals("2h ago", presented.relativeDate)
        assertTrue(presented.canMarkFailed)
        assertFalse(presented.failedImport)
    }

    @Test
    fun `age falls back to hours then minutes`() {
        val hours = ActivityPresenters.present(
            event(1, MovieHistoryEventType.GRABBED, data = mapOf("ageHours" to "90")),
            now = now,
        )
        val minutes = ActivityPresenters.present(
            event(2, MovieHistoryEventType.GRABBED, data = mapOf("ageMinutes" to "90")),
            now = now,
        )

        assertEquals("90h", hours.ageText)
        assertEquals("1h 30m", minutes.ageText)
    }

    @Test
    fun `unknown movie falls back to the release title and unknown dates stay null`() {
        val presented = ActivityPresenters.present(
            HistoryResource(
                id = 3,
                sourceTitle = "Some.Release",
                eventType = MovieHistoryEventType.MOVIE_FILE_RENAMED,
                movie = MovieResource(),
            ),
            now = now,
        )

        assertEquals("Some.Release", presented.title)
        assertNull(presented.date)
        assertNull(presented.relativeDate)
        assertNull(presented.ageText)
    }

    @Test
    fun `failed download with a download id offers the manual import retry`() {
        val presented = ActivityPresenters.present(
            event(2, MovieHistoryEventType.DOWNLOAD_FAILED, downloadId = "dl-2"),
            now = now,
        )

        assertTrue(presented.failedImport)
        assertFalse(presented.canMarkFailed)
    }

    @Test
    fun `present sorts date-descending with the newest id breaking ties`() {
        val presented = ActivityPresenters.present(
            listOf(
                event(1, MovieHistoryEventType.GRABBED, date = "2026-10-18T00:00:00Z"),
                event(5, MovieHistoryEventType.GRABBED, date = "2026-10-20T09:00:00Z"),
                event(4, MovieHistoryEventType.GRABBED, date = "2026-10-20T09:00:00Z"),
            ),
            now = now,
        )

        assertEquals(listOf(5, 4, 1), presented.map { it.id })
    }

    @Test
    fun `filter narrows by type window and query`() {
        val events = ActivityPresenters.present(
            listOf(
                event(1, MovieHistoryEventType.GRABBED, date = "2026-10-20T11:00:00Z"),
                event(2, MovieHistoryEventType.DOWNLOAD_FAILED, date = "2026-10-20T11:00:00Z"),
                event(3, MovieHistoryEventType.GRABBED, date = "2026-10-01T00:00:00Z"),
                event(4, MovieHistoryEventType.MOVIE_FILE_RENAMED, date = "2026-10-20T11:00:00Z"),
            ),
            now = now,
        )

        val byType = ActivityPresenters.filter(
            events, HistoryEventTypeFilter.GRABBED, HistoryDateWindow.ALL_TIME, "", now,
        )
        assertEquals(listOf(1, 3), byType.map { it.id })

        // Same-date events arrive id-descending, so the window keeps that order.
        val byWindow = ActivityPresenters.filter(
            events, HistoryEventTypeFilter.ALL, HistoryDateWindow.LAST_7_DAYS, "", now,
        )
        assertEquals(listOf(4, 2, 1), byWindow.map { it.id })

        val byQuery = ActivityPresenters.filter(
            events, HistoryEventTypeFilter.ALL, HistoryDateWindow.ALL_TIME, "movie 2", now,
        )
        assertEquals(listOf(2), byQuery.map { it.id })

        val byReleaseQuery = ActivityPresenters.filter(
            events, HistoryEventTypeFilter.ALL, HistoryDateWindow.ALL_TIME, "release.4", now,
        )
        assertEquals(listOf(4), byReleaseQuery.map { it.id })
    }

    @Test
    fun `imported and failed chips cover their wire variants`() {
        val events = ActivityPresenters.present(
            listOf(
                event(1, MovieHistoryEventType.DOWNLOAD_FOLDER_IMPORTED),
                event(2, MovieHistoryEventType.MOVIE_FOLDER_IMPORTED),
                event(3, MovieHistoryEventType.DOWNLOAD_FAILED),
                event(4, MovieHistoryEventType.DOWNLOAD_IGNORED),
            ),
            now = now,
        )

        val imported = ActivityPresenters.filter(
            events, HistoryEventTypeFilter.IMPORTED, HistoryDateWindow.ALL_TIME, "", now,
        )
        val failed = ActivityPresenters.filter(
            events, HistoryEventTypeFilter.FAILED, HistoryDateWindow.ALL_TIME, "", now,
        )

        assertEquals(listOf(2, 1), imported.map { it.id })
        assertEquals(listOf(4, 3), failed.map { it.id })
    }

    @Test
    fun `needsOlderPages stops once the oldest row predates the window`() {
        val events = ActivityPresenters.present(
            listOf(
                event(1, MovieHistoryEventType.GRABBED, date = "2026-10-20T11:00:00Z"),
                event(2, MovieHistoryEventType.GRABBED, date = "2026-10-19T00:00:00Z"),
            ),
        )
        val window = HistoryDateWindow.LAST_24_HOURS

        assertTrue(
            ActivityPresenters.needsOlderPages(
                events.take(1), window, endReached = false, now = now,
            ),
        )
        assertFalse(
            ActivityPresenters.needsOlderPages(events, window, endReached = false, now = now),
        )
        assertFalse(
            ActivityPresenters.needsOlderPages(events.take(1), window, endReached = true, now = now),
        )
        assertTrue(
            ActivityPresenters.needsOlderPages(
                emptyList(), HistoryDateWindow.ALL_TIME, endReached = false, now = now,
            ),
        )
    }

    @Test
    fun `blocklist presentation carries reason release info and formats`() {
        val entries = ActivityPresenters.presentBlocklist(
            listOf(
                BlocklistResource(
                    id = 1,
                    movieId = 7,
                    sourceTitle = "Bad.Release",
                    languages = listOf(Language(id = 1, name = "English")),
                    quality = QualityModel(quality = Quality(id = 1, name = "bluray-2160p")),
                    customFormats = listOf(CustomFormatResource(id = 2, name = "HQ")),
                    date = "2026-10-20T10:00:00Z",
                    protocol = DownloadProtocol.TORRENT,
                    indexer = "Indexer A",
                    message = "Release was blocklisted by user",
                    movie = MovieResource(id = 7, title = "Movie 7", year = 2019),
                ),
                BlocklistResource(
                    id = 2,
                    sourceTitle = "Fallback.Release",
                    date = "2026-10-21T10:00:00Z",
                ),
            ),
        )

        val first = entries.last { it.id == 1 }
        assertEquals("Movie 7", first.title)
        assertEquals(2019, first.movieYear)
        assertEquals("Bad.Release", first.sourceTitle)
        assertEquals("BluRay 2160p", first.qualityName)
        assertEquals(DownloadProtocol.TORRENT, first.protocol)
        assertEquals("Indexer A", first.indexer)
        assertEquals("Release was blocklisted by user", first.reason)
        assertEquals(listOf("English"), first.languageNames)
        assertEquals(listOf("HQ"), first.customFormatNames)

        // Date-descending: the entry from Oct 21 sorts first despite id order.
        assertEquals(listOf(2, 1), entries.map { it.id })
        assertEquals("Fallback.Release", entries.first().title)
    }

    @Test
    fun `queued ids badge their rows`() {
        val events = ActivityPresenters.present(
            listOf(event(1, MovieHistoryEventType.GRABBED)),
            queuedIds = setOf(1),
        )

        assertTrue(events.single().queuedOffline)
    }
}
