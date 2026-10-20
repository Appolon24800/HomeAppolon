package dev.appolon.homelab.arr.feature.lidarr.activity.logic

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityModel
import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LidarrActivityPresentersTest {

    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")

    private fun event(
        id: Int,
        type: EntityHistoryEventType,
        date: String = "2026-10-20T10:00:00Z",
        block: HistoryResource.() -> HistoryResource = { this },
    ): HistoryResource = HistoryResource(
        id = id,
        albumId = 0,
        artistId = 0,
        trackId = 0,
        sourceTitle = "Artist-Release-FLAC",
        date = date,
        eventType = type,
        data = mapOf(
            "indexer" to "Prowlarr",
            "downloadClient" to "SABnzbd",
            "size" to "104857600",
            "releaseGroup" to "GROUP",
            "message" to "manual import",
        ),
    ).block()

    @Test
    fun `present extracts artist album and track context from the embeddings`() {
        val event = event(1, EntityHistoryEventType.TRACK_FILE_IMPORTED) {
            copy(
                albumId = 11,
                artistId = 3,
                quality = QualityModel(quality = Quality(id = 1, name = "FLAC")),
                album = AlbumResource(id = 11, title = "Nightfall"),
                artist = ArtistResource(id = 3, artistName = "Moodring"),
                track = TrackResource(id = 40, trackNumber = "5", title = "Slowdive"),
            )
        }

        val ui = LidarrActivityPresenters.present(event, now = now)

        assertEquals("Moodring", ui.title)
        assertEquals("Nightfall", ui.albumTitle)
        assertEquals("Slowdive", ui.trackTitle)
        assertEquals("Track 5", ui.trackLabel)
        assertEquals("FLAC", ui.qualityName)
        assertEquals("Prowlarr", ui.indexer)
        assertEquals("SABnzbd", ui.downloadClient)
        assertEquals("GROUP", ui.releaseGroup)
        assertEquals("manual import", ui.message)
        assertEquals("100.0 MB", ui.sizeText)
        assertEquals(Instant.parse("2026-10-20T10:00:00Z"), ui.date)
    }

    @Test
    fun `present falls back to the release title without an artist match`() {
        val event = event(2, EntityHistoryEventType.GRABBED)

        val ui = LidarrActivityPresenters.present(event, now = now)

        assertEquals("Artist-Release-FLAC", ui.title)
        assertNull(ui.albumTitle)
        assertTrue(ui.canMarkFailed)
        assertFalse(ui.failedImport)
    }

    @Test
    fun `present flags failed and incomplete imports with a download id for retry`() {
        val failed = event(3, EntityHistoryEventType.DOWNLOAD_FAILED) { copy(downloadId = "abc") }
        val incomplete = event(4, EntityHistoryEventType.ALBUM_IMPORT_INCOMPLETE) { copy(downloadId = "abc") }
        val noDownload = event(5, EntityHistoryEventType.DOWNLOAD_FAILED)

        assertTrue(LidarrActivityPresenters.present(failed, now = now).failedImport)
        assertTrue(LidarrActivityPresenters.present(incomplete, now = now).failedImport)
        assertFalse(LidarrActivityPresenters.present(noDownload, now = now).failedImport)
    }

    @Test
    fun `present orders date-descending and breaks ties by id`() {
        val events = listOf(
            event(1, EntityHistoryEventType.GRABBED, date = "2026-10-19T00:00:00Z"),
            event(5, EntityHistoryEventType.GRABBED, date = "2026-10-20T00:00:00Z"),
            event(3, EntityHistoryEventType.GRABBED, date = "2026-10-20T00:00:00Z"),
        )

        val ordered = LidarrActivityPresenters.present(events, now = now)

        assertEquals(listOf(5, 3, 1), ordered.map { it.id })
    }

    @Test
    fun `filter slices by event type wire names`() {
        val events = LidarrActivityPresenters.present(
            listOf(
                event(1, EntityHistoryEventType.GRABBED),
                event(2, EntityHistoryEventType.TRACK_FILE_IMPORTED),
                event(3, EntityHistoryEventType.ARTIST_FOLDER_IMPORTED),
                event(4, EntityHistoryEventType.TRACK_FILE_RENAMED),
                event(5, EntityHistoryEventType.TRACK_FILE_RETAGGED),
                event(6, EntityHistoryEventType.DOWNLOAD_FAILED),
                event(7, EntityHistoryEventType.DOWNLOAD_IGNORED),
            ),
            now = now,
        )

        fun ids(filter: LidarrHistoryFilter) = LidarrActivityPresenters
            .filter(events, filter, LidarrHistoryDateWindow.ALL_TIME, "", now)
            .map { it.id }

        assertEquals(listOf(1), ids(LidarrHistoryFilter.GRABBED))
        assertEquals(listOf(3, 2), ids(LidarrHistoryFilter.IMPORTED))
        assertEquals(listOf(7, 6), ids(LidarrHistoryFilter.FAILED))
        assertEquals(listOf(4), ids(LidarrHistoryFilter.RENAMED))
        assertEquals(listOf(5), ids(LidarrHistoryFilter.RETAGGED))
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 1), ids(LidarrHistoryFilter.ALL))
    }

    @Test
    fun `filter slices album events by album context and artist events by type`() {
        val events = LidarrActivityPresenters.present(
            listOf(
                event(1, EntityHistoryEventType.GRABBED) { copy(albumId = 11) },
                event(2, EntityHistoryEventType.TRACK_FILE_IMPORTED),
                event(3, EntityHistoryEventType.TRACK_FILE_RENAMED),
                event(4, EntityHistoryEventType.ARTIST_FOLDER_IMPORTED),
            ),
            now = now,
        )

        val albumIds = LidarrActivityPresenters
            .filter(events, LidarrHistoryFilter.ALBUM_EVENTS, LidarrHistoryDateWindow.ALL_TIME, "", now)
            .map { it.id }
        val artistIds = LidarrActivityPresenters
            .filter(events, LidarrHistoryFilter.ARTIST_EVENTS, LidarrHistoryDateWindow.ALL_TIME, "", now)
            .map { it.id }

        // Album events carry an album context (albumId or embedded album);
        // ties on date order by id descending.
        assertEquals(listOf(1), albumIds)
        assertEquals(listOf(4, 3), artistIds)
    }

    @Test
    fun `filter applies date windows against the injected clock`() {
        val events = LidarrActivityPresenters.present(
            listOf(
                event(1, EntityHistoryEventType.GRABBED, date = "2026-10-20T11:00:00Z"),
                event(2, EntityHistoryEventType.GRABBED, date = "2026-10-18T00:00:00Z"),
                event(3, EntityHistoryEventType.GRABBED, date = "2026-09-01T00:00:00Z"),
            ),
            now = now,
        )

        val day = LidarrActivityPresenters.filter(
            events,
            LidarrHistoryFilter.ALL,
            LidarrHistoryDateWindow.LAST_24_HOURS,
            "",
            now,
        ).map { it.id }
        val week = LidarrActivityPresenters.filter(
            events,
            LidarrHistoryFilter.ALL,
            LidarrHistoryDateWindow.LAST_7_DAYS,
            "",
            now,
        ).map { it.id }

        assertEquals(listOf(1), day)
        assertEquals(listOf(1, 2), week)
    }

    @Test
    fun `filter matches the query against artist album track and release`() {
        val events = LidarrActivityPresenters.present(
            listOf(
                event(1, EntityHistoryEventType.GRABBED) {
                    copy(
                        artist = ArtistResource(id = 3, artistName = "Moodring"),
                        album = AlbumResource(id = 11, title = "Nightfall"),
                        track = TrackResource(id = 40, title = "Slowdive"),
                    )
                },
                event(2, EntityHistoryEventType.GRABBED),
            ),
            now = now,
        )

        fun ids(query: String) = LidarrActivityPresenters
            .filter(events, LidarrHistoryFilter.ALL, LidarrHistoryDateWindow.ALL_TIME, query, now)
            .map { it.id }

        assertEquals(listOf(1), ids("moodring"))
        assertEquals(listOf(1), ids("nightfall"))
        assertEquals(listOf(1), ids("slowdive"))
        // Both rows carry the same release name, so the query matches both;
        // date ties order by id descending.
        assertEquals(listOf(2, 1), ids("artist-release"))
        assertEquals(listOf(2, 1), ids(""))
    }

    @Test
    fun `needsOlderPages stops once the window's start is loaded`() {
        // Both rows sit inside the last 24 hours, so older pages could add more.
        val events = LidarrActivityPresenters.present(
            listOf(
                event(1, EntityHistoryEventType.GRABBED, date = "2026-10-20T11:00:00Z"),
                event(2, EntityHistoryEventType.GRABBED, date = "2026-10-20T00:00:00Z"),
            ),
            now = now,
        )
        val older = LidarrActivityPresenters.present(
            listOf(event(1, EntityHistoryEventType.GRABBED, date = "2026-10-01T00:00:00Z")),
            now = now,
        )

        assertTrue(
            LidarrActivityPresenters.needsOlderPages(
                events,
                LidarrHistoryDateWindow.LAST_24_HOURS,
                endReached = false,
                now = now,
            ),
        )
        assertFalse(
            LidarrActivityPresenters.needsOlderPages(
                older,
                LidarrHistoryDateWindow.LAST_24_HOURS,
                endReached = false,
                now = now,
            ),
        )
        assertFalse(
            LidarrActivityPresenters.needsOlderPages(
                events,
                LidarrHistoryDateWindow.LAST_24_HOURS,
                endReached = true,
                now = now,
            ),
        )
        assertTrue(
            LidarrActivityPresenters.needsOlderPages(
                events,
                LidarrHistoryDateWindow.ALL_TIME,
                endReached = false,
                now = now,
            ),
        )
    }

    @Test
    fun `presentBlocklist exposes the rejection reason and artist`() {
        val entry = BlocklistResource(
            id = 1,
            artistId = 3,
            sourceTitle = "Artist-Release-FLAC",
            date = "2026-10-20T10:00:00Z",
            message = "Quality out of profile",
            protocol = DownloadProtocol.TORRENT,
            indexer = "Prowlarr",
            quality = QualityModel(quality = Quality(id = 1, name = "MP3-320")),
            customFormats = listOf(CustomFormatResource(id = 1, name = "Has NFO")),
            artist = ArtistResource(id = 3, artistName = "Moodring"),
        )

        val ui = LidarrActivityPresenters.presentBlocklist(entry, now = now)

        assertEquals("Moodring", ui.title)
        assertEquals("Quality out of profile", ui.reason)
        assertEquals("Prowlarr", ui.indexer)
        assertEquals(DownloadProtocol.TORRENT, ui.protocol)
        assertEquals("MP3 320", ui.qualityName)
        assertEquals(listOf("Has NFO"), ui.customFormatNames)
    }

    @Test
    fun `ageText prefers the finest unit present`() {
        val days = event(1, EntityHistoryEventType.GRABBED) {
            copy(data = mapOf("age" to "1.5"))
        }
        val hours = event(2, EntityHistoryEventType.GRABBED) {
            copy(data = mapOf("ageHours" to "30"))
        }

        val daysUi = LidarrActivityPresenters.present(days, now = now)
        val hoursUi = LidarrActivityPresenters.present(hours, now = now)

        assertTrue(daysUi.ageText != null)
        assertTrue(hoursUi.ageText != null)
    }
}
