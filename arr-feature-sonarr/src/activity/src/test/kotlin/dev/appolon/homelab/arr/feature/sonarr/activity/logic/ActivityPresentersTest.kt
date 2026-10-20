package dev.appolon.homelab.arr.feature.sonarr.activity.logic

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeHistoryEventType
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityModel
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityPresentersTest {

    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")

    private fun seriesObject(title: String? = null, year: Int? = null): SeriesResource =
        SeriesResource(title = title, year = year ?: 0)

    private fun episodeObject(
        season: Int? = null,
        episode: Int? = null,
        title: String? = null,
    ): EpisodeResource = EpisodeResource(
        seasonNumber = season ?: 0,
        episodeNumber = episode ?: 0,
        title = title,
    )

    private fun event(
        id: Int,
        eventType: EpisodeHistoryEventType,
        date: String = "2026-10-20T10:00:00Z",
        seriesTitle: String? = "Series $id",
        season: Int? = 1,
        episode: Int? = 2,
        episodeTitle: String? = "Episode Title $id",
        sourceTitle: String? = "Release.$id",
        downloadId: String? = null,
        data: Map<String, String?>? = null,
    ) = HistoryResource(
        id = id,
        episodeId = id * 10,
        seriesId = id * 100,
        sourceTitle = sourceTitle,
        quality = QualityModel(quality = Quality(id = 1, name = "WEBDL-1080p")),
        date = date,
        downloadId = downloadId,
        eventType = eventType,
        data = data,
        episode = episodeObject(season, episode, episodeTitle),
        series = seriesObject(seriesTitle, 2020),
    )

    @Test
    fun `present builds an episode-granular row from the embeddings`() {
        val presented = ActivityPresenters.present(
            event(
                1,
                EpisodeHistoryEventType.GRABBED,
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

        assertEquals("Series 1", presented.title)
        assertEquals(2020, presented.seriesYear)
        assertEquals("S01E02", presented.episodeLabel)
        assertEquals("Episode Title 1", presented.episodeTitle)
        assertEquals(10, presented.episodeId)
        assertEquals(100, presented.seriesId)
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
            event(1, EpisodeHistoryEventType.GRABBED, data = mapOf("ageHours" to "90")),
            now = now,
        )
        val minutes = ActivityPresenters.present(
            event(2, EpisodeHistoryEventType.GRABBED, data = mapOf("ageMinutes" to "90")),
            now = now,
        )

        assertEquals("90h", hours.ageText)
        assertEquals("1h 30m", minutes.ageText)
    }

    @Test
    fun `missing episode parts drop the label and unknown series falls back to the release title`() {
        val presented = ActivityPresenters.present(
            HistoryResource(
                id = 3,
                sourceTitle = "Some.Release",
                // Unparseable on purpose: the presenter must treat it as absent.
                date = "not-a-date",
                eventType = EpisodeHistoryEventType.EPISODE_FILE_RENAMED,
            ),
            now = now,
        )

        assertEquals("Some.Release", presented.title)
        assertNull(presented.episodeLabel)
        assertNull(presented.episodeTitle)
        assertNull(presented.seriesYear)
        assertNull(presented.date)
        assertNull(presented.relativeDate)
        assertNull(presented.ageText)
    }

    @Test
    fun `failed download with a download id offers the manual import retry`() {
        val presented = ActivityPresenters.present(
            event(2, EpisodeHistoryEventType.DOWNLOAD_FAILED, downloadId = "dl-2"),
            now = now,
        )

        assertTrue(presented.failedImport)
        assertFalse(presented.canMarkFailed)
        assertEquals("dl-2", presented.downloadId)
    }

    @Test
    fun `present sorts date-descending with the newest id breaking ties`() {
        val presented = ActivityPresenters.present(
            listOf(
                event(1, EpisodeHistoryEventType.GRABBED, date = "2026-10-18T00:00:00Z"),
                event(5, EpisodeHistoryEventType.GRABBED, date = "2026-10-20T09:00:00Z"),
                event(4, EpisodeHistoryEventType.GRABBED, date = "2026-10-20T09:00:00Z"),
            ),
            now = now,
        )

        assertEquals(listOf(5, 4, 1), presented.map { it.id })
    }

    @Test
    fun `filter narrows by type window and query`() {
        val events = ActivityPresenters.present(
            listOf(
                event(1, EpisodeHistoryEventType.GRABBED, date = "2026-10-20T11:00:00Z"),
                event(2, EpisodeHistoryEventType.DOWNLOAD_FAILED, date = "2026-10-20T11:00:00Z"),
                event(3, EpisodeHistoryEventType.GRABBED, date = "2026-10-01T00:00:00Z"),
                event(4, EpisodeHistoryEventType.EPISODE_FILE_RENAMED, date = "2026-10-20T11:00:00Z"),
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

        val bySeriesQuery = ActivityPresenters.filter(
            events, HistoryEventTypeFilter.ALL, HistoryDateWindow.ALL_TIME, "series 2", now,
        )
        assertEquals(listOf(2), bySeriesQuery.map { it.id })

        val byEpisodeQuery = ActivityPresenters.filter(
            events, HistoryEventTypeFilter.ALL, HistoryDateWindow.ALL_TIME, "episode title 4", now,
        )
        assertEquals(listOf(4), byEpisodeQuery.map { it.id })

        val byReleaseQuery = ActivityPresenters.filter(
            events, HistoryEventTypeFilter.ALL, HistoryDateWindow.ALL_TIME, "release.4", now,
        )
        assertEquals(listOf(4), byReleaseQuery.map { it.id })
    }

    @Test
    fun `imported and failed chips cover their wire variants`() {
        val events = ActivityPresenters.present(
            listOf(
                event(1, EpisodeHistoryEventType.DOWNLOAD_FOLDER_IMPORTED),
                event(2, EpisodeHistoryEventType.SERIES_FOLDER_IMPORTED),
                event(3, EpisodeHistoryEventType.DOWNLOAD_FAILED),
                event(4, EpisodeHistoryEventType.DOWNLOAD_IGNORED),
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
                event(1, EpisodeHistoryEventType.GRABBED, date = "2026-10-20T11:00:00Z"),
                event(2, EpisodeHistoryEventType.GRABBED, date = "2026-10-19T00:00:00Z"),
            ),
            now = now,
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
                    seriesId = 7,
                    sourceTitle = "Bad.Release",
                    languages = listOf(Language(id = 1, name = "English")),
                    quality = QualityModel(quality = Quality(id = 1, name = "bluray-2160p")),
                    customFormats = listOf(CustomFormatResource(id = 2, name = "HQ")),
                    date = "2026-10-20T10:00:00Z",
                    protocol = DownloadProtocol.TORRENT,
                    indexer = "Indexer A",
                    message = "Release was blocklisted by user",
                    series = seriesObject("Series 7", 2019),
                ),
                BlocklistResource(
                    id = 2,
                    sourceTitle = "Fallback.Release",
                    date = "2026-10-21T10:00:00Z",
                ),
            ),
            now = now,
        )

        val first = entries.last { it.id == 1 }
        assertEquals("Series 7", first.title)
        assertEquals(2019, first.seriesYear)
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
            listOf(event(1, EpisodeHistoryEventType.GRABBED)),
            queuedIds = setOf(1),
        )

        assertTrue(events.single().queuedOffline)
    }
}
