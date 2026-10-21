package dev.appolon.homelab.arr.feature.lidarr.calendar.data

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MediaCover
import dev.appolon.homelab.arr.api.lidarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.core.ArrImages
import dev.appolon.homelab.arr.core.CoverType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LidarrCalendarModelsTest {

    @Test
    fun `calendarEntryOf maps artist album and release type with the local cover path`() {
        val album = AlbumResource(
            id = 7,
            title = "Nightfall",
            artistId = 3,
            monitored = true,
            albumType = "Album",
            secondaryTypes = listOf("Deluxe Edition"),
            releaseDate = "2026-10-20T00:00:00Z",
            images = listOf(MediaCover(url = "/Albums/7/cover.jpg", coverType = MediaCoverTypes.COVER)),
            artist = ArtistResource(id = 3, artistName = "Moodring", sortName = "Moodring", monitored = true),
        )

        val entry = calendarEntryOf(album)!!

        assertEquals(7, entry.albumId)
        assertEquals(3, entry.artistId)
        assertEquals("Moodring", entry.artistTitle)
        assertEquals("Nightfall", entry.albumTitle)
        assertEquals("Album", entry.albumType)
        assertEquals(listOf("Deluxe Edition"), entry.secondaryTypes)
        assertEquals("2026-10-20", entry.releaseDate)
        assertTrue(entry.monitored)
        assertTrue(entry.artistMonitored)
        assertEquals(ArrImages.lidarrAlbumCover(7, CoverType.COVER), entry.coverPath)
        assertNull(entry.remoteCover)
    }

    @Test
    fun `calendarEntryOf rejects placeholder and unparseable release dates`() {
        val placeholder = AlbumResource(id = 1, releaseDate = "0001-01-01T00:00:00Z")
        assertNull(calendarEntryOf(placeholder))

        val missing = AlbumResource(id = 2, releaseDate = null)
        assertNull(calendarEntryOf(missing))

        val garbage = AlbumResource(id = 3, releaseDate = "not a date")
        assertNull(calendarEntryOf(garbage))
    }

    @Test
    fun `calendarEntryOf prefers absolute cover urls and keeps remote fallbacks`() {
        val album = AlbumResource(
            id = 9,
            releaseDate = "2026-11-01",
            images = listOf(
                MediaCover(
                    url = "https://cdn.example/cover.jpg",
                    coverType = MediaCoverTypes.COVER,
                    remoteUrl = "https://fanart.example/9.jpg",
                ),
            ),
        )

        val entry = calendarEntryOf(album)!!

        assertEquals("https://cdn.example/cover.jpg", entry.coverPath)
        assertEquals("https://fanart.example/9.jpg", entry.remoteCover)
    }

    @Test
    fun `releaseStateOf splits on the calendar day`() {
        val entry = LidarrCalendarEntry(albumId = 1, artistId = 1, releaseDate = "2026-10-20")
        assertEquals(
            CalendarReleaseState.RELEASED,
            releaseStateOf(entry, LocalDate.of(2026, 10, 21)),
        )
        assertEquals(
            CalendarReleaseState.UPCOMING,
            releaseStateOf(entry, LocalDate.of(2026, 10, 20)),
        )
    }

    @Test
    fun `countUpcoming counts distinct monitored artists inside the horizon`() {
        val today = LocalDate.of(2026, 10, 20)
        val entries = listOf(
            LidarrCalendarEntry(albumId = 1, artistId = 1, releaseDate = "2026-10-21", monitored = true),
            LidarrCalendarEntry(albumId = 2, artistId = 1, releaseDate = "2026-10-22", monitored = true),
            LidarrCalendarEntry(albumId = 3, artistId = 2, releaseDate = "2026-10-26", monitored = true),
            // Outside the horizon, unmonitored, and in the past.
            LidarrCalendarEntry(albumId = 4, artistId = 3, releaseDate = "2026-10-28", monitored = true),
            LidarrCalendarEntry(albumId = 5, artistId = 4, releaseDate = "2026-10-21", monitored = false),
            LidarrCalendarEntry(albumId = 6, artistId = 5, releaseDate = "2026-10-19", monitored = true),
        )

        assertEquals(2, countUpcoming(entries, today, horizonDays = 7))
    }
}
