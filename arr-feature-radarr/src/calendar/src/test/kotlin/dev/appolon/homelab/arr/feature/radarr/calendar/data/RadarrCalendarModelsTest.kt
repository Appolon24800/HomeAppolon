package dev.appolon.homelab.arr.feature.radarr.calendar.data

import dev.appolon.homelab.arr.api.radarr.model.MediaCover
import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarrCalendarModelsTest {

    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun `movie with all four dates maps to one entry per date kind`() {
        val entries = calendarEntriesOf(
            movie(
                id = 7,
                title = "Arrival",
                inCinemas = "2026-11-10",
                physicalRelease = "2026-12-01T00:00:00Z",
                digitalRelease = "2026-11-25T00:00:00Z",
                releaseDate = "2026-12-01",
            ),
        )
        assertEquals(
            listOf(
                // Mapping keeps the spec's field order; presentation layers sort by date.
                LocalDate.of(2026, 11, 10).toString() to CalendarEventKind.IN_CINEMAS,
                LocalDate.of(2026, 12, 1).toString() to CalendarEventKind.PHYSICAL_RELEASE,
                LocalDate.of(2026, 11, 25).toString() to CalendarEventKind.DIGITAL_RELEASE,
                LocalDate.of(2026, 12, 1).toString() to CalendarEventKind.RELEASE,
            ),
            entries.map { it.eventDate to it.eventKind },
        )
        entries.forEach { entry ->
            assertEquals(7, entry.movieId)
            assertEquals("Arrival", entry.title)
        }
    }

    @Test
    fun `movie without parseable dates maps to nothing`() {
        assertTrue(calendarEntriesOf(movie(id = 1, title = "Mystery")).isEmpty())
    }

    @Test
    fun `local poster url becomes a MediaCover path and absolute urls pass through`() {
        val local = calendarEntriesOf(
            movie(
                id = 5,
                title = "Local",
                inCinemas = "2026-10-05",
                images = listOf(MediaCover(coverType = MediaCoverTypes.POSTER, url = "/radarr-cover")),
            ),
        ).single()
        assertEquals("api/v3/mediacover/5/poster.jpg", local.posterPath)

        val absolute = calendarEntriesOf(
            movie(
                id = 6,
                title = "Remote",
                inCinemas = "2026-10-05",
                images = listOf(
                    MediaCover(
                        coverType = MediaCoverTypes.POSTER,
                        url = "https://image.tmdb.org/t/p/w500/abc.jpg",
                    ),
                ),
            ),
        ).single()
        assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg", absolute.posterPath)

        val fallback = calendarEntriesOf(
            movie(
                id = 8,
                title = "Bare",
                inCinemas = "2026-10-05",
                remotePoster = "/xyz.jpg",
            ),
        ).single()
        assertNull(fallback.posterPath)
        assertEquals("/xyz.jpg", fallback.remotePoster)
    }

    @Test
    fun `availability follows date and file state`() {
        val hasFile = entry(date = "2026-10-21", hasFile = true, status = MovieStatusType.ANNOUNCED)
        assertEquals(CalendarAvailability.RELEASED, availabilityOf(hasFile, today))

        val past = entry(date = "2026-10-19", status = MovieStatusType.ANNOUNCED)
        assertEquals(CalendarAvailability.RELEASED, availabilityOf(past, today))

        val futureCinema = entry(date = "2026-10-21", kind = CalendarEventKind.IN_CINEMAS)
        assertEquals(CalendarAvailability.IN_CINEMAS, availabilityOf(futureCinema, today))

        val futureStatusCinema = entry(date = "2026-10-21", status = MovieStatusType.IN_CINEMAS)
        assertEquals(CalendarAvailability.IN_CINEMAS, availabilityOf(futureStatusCinema, today))

        val futureDigital = entry(date = "2026-10-21", kind = CalendarEventKind.DIGITAL_RELEASE)
        assertEquals(CalendarAvailability.ANNOUNCED, availabilityOf(futureDigital, today))
    }

    @Test
    fun `upcoming count dedupes movies inside the horizon`() {
        val entries = listOf(
            entry(movieId = 1, date = "2026-10-21"),
            entry(movieId = 1, date = "2026-10-24"),
            entry(movieId = 2, date = "2026-10-27"),
            entry(movieId = 3, date = "2026-11-15"),
            entry(movieId = 4, date = "2026-10-19"),
            entry(movieId = 5, date = "2026-10-20"),
        )
        assertEquals(3, countUpcoming(entries, today, horizonDays = 7))
    }

    @Test
    fun `window contains its bounds and round-trips its id`() {
        val window = CalendarWindow(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 11, 7))
        assertTrue(window.contains(LocalDate.of(2026, 9, 24)))
        assertTrue(window.contains(LocalDate.of(2026, 11, 7)))
        assertTrue(!window.contains(LocalDate.of(2026, 11, 8)))
        assertEquals(window, CalendarWindow.of(window.id(unmonitored = true)))
        assertEquals("2026-09-24:2026-11-07:true", window.id(unmonitored = true))
    }

    private fun movie(
        id: Int,
        title: String,
        inCinemas: String? = null,
        physicalRelease: String? = null,
        digitalRelease: String? = null,
        releaseDate: String? = null,
        images: List<MediaCover>? = null,
        remotePoster: String? = null,
    ): MovieResource = MovieResource(
        id = id,
        title = title,
        inCinemas = inCinemas,
        physicalRelease = physicalRelease,
        digitalRelease = digitalRelease,
        releaseDate = releaseDate,
        images = images,
        remotePoster = remotePoster,
    )

    private fun entry(
        movieId: Int = 1,
        date: String,
        kind: CalendarEventKind = CalendarEventKind.RELEASE,
        hasFile: Boolean = false,
        status: MovieStatusType? = null,
    ): RadarrCalendarEntry = RadarrCalendarEntry(
        movieId = movieId,
        title = "Movie $movieId",
        eventDate = date,
        eventKind = kind,
        hasFile = hasFile,
        status = status,
    )
}
