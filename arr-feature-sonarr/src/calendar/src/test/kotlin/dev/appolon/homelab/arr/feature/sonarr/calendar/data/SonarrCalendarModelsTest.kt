package dev.appolon.homelab.arr.feature.sonarr.calendar.data

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.MediaCover
import dev.appolon.homelab.arr.api.sonarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonarrCalendarModelsTest {

    @Test
    fun `calendarEntryOf maps episode fields and the SxxEyy code`() {
        val entry = calendarEntryOf(
            episode(
                id = 12,
                seriesId = 3,
                season = 2,
                number = 7,
                airDate = "2026-10-05",
                airDateUtc = "2026-10-05T02:00:00Z",
                title = "The One With the Test",
                monitored = true,
                hasFile = false,
            ),
        )!!

        assertEquals(12, entry.episodeId)
        assertEquals(3, entry.seriesId)
        assertEquals("Firefly", entry.seriesTitle)
        assertEquals(2, entry.seasonNumber)
        assertEquals(7, entry.episodeNumber)
        assertEquals("S02E07", entry.episodeCode)
        assertEquals("The One With the Test", entry.title)
        assertEquals("2026-10-05", entry.airDate)
        assertEquals("2026-10-05T02:00:00Z", entry.airDateUtc)
        assertTrue(entry.monitored)
        assertTrue(!entry.hasFile)
        assertTrue(entry.seriesMonitored)
    }

    @Test
    fun `poster prefers the local mediacover path and falls back to remote art`() {
        val local = calendarEntryOf(
            episode(
                id = 1,
                airDate = "2026-10-05",
                series = series(
                    id = 5,
                    images = listOf(MediaCover(coverType = MediaCoverTypes.POSTER, url = "/MediaCover/5/poster.jpg")),
                ),
            ),
        )!!
        assertEquals("/api/v3/mediacover/5/poster-500.jpg", local.posterPath)

        val absolute = calendarEntryOf(
            episode(
                id = 2,
                airDate = "2026-10-05",
                series = series(
                    id = 6,
                    images = listOf(
                        MediaCover(
                            coverType = MediaCoverTypes.POSTER,
                            url = "https://cdn.example/p.jpg",
                            remoteUrl = "https://image.tmdb.org/t/p/original/abc.jpg",
                        ),
                    ),
                ),
            ),
        )!!
        assertEquals("https://cdn.example/p.jpg", absolute.posterPath)

        val remoteOnly = calendarEntryOf(
            episode(
                id = 3,
                airDate = "2026-10-05",
                series = series(
                    id = 7,
                    images = listOf(
                        MediaCover(coverType = MediaCoverTypes.POSTER, remoteUrl = "/remote.jpg"),
                    ),
                    remotePoster = "series-fallback.jpg",
                ),
            ),
        )!!
        assertNull(remoteOnly.posterPath)
        // The cover's remote art wins over the series-level remote poster.
        assertEquals("/remote.jpg", remoteOnly.remotePoster)
    }

    @Test
    fun `episodes without an air date map to nothing`() {
        assertNull(calendarEntryOf(episode(id = 1, airDate = null)))
        assertNull(calendarEntryOf(episode(id = 2, airDate = "")))
    }

    @Test
    fun `series fallback kicks in when the embedded series is absent`() {
        val entry = calendarEntryOf(
            episode(id = 9, seriesId = 42, airDate = "2026-10-05", series = SeriesResource()),
        )!!

        assertEquals(42, entry.seriesId)
        assertNull(entry.seriesTitle)
        assertNull(entry.sortTitle)
    }

    @Test
    fun `airStateOf flips on the calendar day`() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals(CalendarAirState.AIRED, airStateOf(entry(1, "2026-10-19"), today))
        assertEquals(CalendarAirState.NOT_YET_AIRED, airStateOf(entry(2, "2026-10-20"), today))
        assertEquals(CalendarAirState.NOT_YET_AIRED, airStateOf(entry(3, "2026-10-21"), today))
    }

    @Test
    fun `countUpcoming counts distinct series within the horizon`() {
        val today = LocalDate.of(2026, 10, 20)
        val entries = listOf(
            entry(episodeId = 11, airDate = "2026-10-20", seriesId = 1, monitored = true),
            entry(episodeId = 12, airDate = "2026-10-24", seriesId = 1, monitored = true),
            entry(episodeId = 21, airDate = "2026-10-27", seriesId = 2, monitored = true),
            entry(episodeId = 22, airDate = "2026-10-28", seriesId = 2, monitored = true),
            entry(episodeId = 31, airDate = "2026-10-22", seriesId = 3, monitored = false),
            entry(episodeId = 41, airDate = "2026-11-15", seriesId = 4, monitored = true),
            entry(episodeId = 51, airDate = "2026-10-19", seriesId = 5, monitored = true),
        )

        assertEquals(2, countUpcoming(entries, today, horizonDays = 7))
        assertEquals(3, countUpcoming(entries, today, horizonDays = 30))
    }

    @Test
    fun `entries sort by series title then season and episode`() {
        val entries = listOf(
            episode(id = 4, airDate = "2026-10-05", sortTitle = "zeta", season = 1, number = 1),
            episode(id = 5, airDate = "2026-10-05", sortTitle = "alpha", season = 2, number = 3),
            episode(id = 6, airDate = "2026-10-05", sortTitle = "alpha", season = 2, number = 1),
            episode(id = 8, airDate = "2026-10-05", sortTitle = null, season = 0, number = 1),
        ).mapNotNull(::calendarEntryOf).sorted()

        assertEquals(
            listOf("alpha S02E01", "alpha S02E03", "firefly S00E01", "zeta S01E01"),
            entries.map { (it.sortTitle ?: it.seriesTitle ?: "").lowercase() + " " + it.episodeCode },
        )
        // The null-sortTitle series falls back to its title and sorts by that.
        assertEquals("Firefly", entries[2].seriesTitle)
    }

    @Test
    fun `episodeCodeOf is locale neutral and zero padded`() {
        assertEquals("S01E05", episodeCodeOf(1, 5))
        assertEquals("S10E12", episodeCodeOf(10, 12))
        assertEquals("S00E01", episodeCodeOf(0, 1))
    }

    private fun entry(
        episodeId: Int,
        airDate: String,
        seriesId: Int = 1,
        monitored: Boolean = true,
    ): SonarrCalendarEntry = SonarrCalendarEntry(
        episodeId = episodeId,
        seriesId = seriesId,
        airDate = airDate,
        monitored = monitored,
    )

    private fun episode(
        id: Int,
        airDate: String?,
        seriesId: Int = 1,
        season: Int = 1,
        number: Int = 1,
        airDateUtc: String? = null,
        title: String? = "Episode",
        monitored: Boolean = true,
        hasFile: Boolean = false,
        sortTitle: String? = null,
        series: SeriesResource = series(id = seriesId, sortTitle = sortTitle),
    ): EpisodeResource = EpisodeResource(
        id = id,
        seriesId = seriesId,
        seasonNumber = season,
        episodeNumber = number,
        title = title,
        airDate = airDate,
        airDateUtc = airDateUtc,
        monitored = monitored,
        hasFile = hasFile,
        series = series,
    )

    private fun series(
        id: Int,
        title: String = "Firefly",
        sortTitle: String? = title,
        images: List<MediaCover> = emptyList(),
        remotePoster: String? = null,
    ): SeriesResource = SeriesResource(
        id = id,
        title = title,
        sortTitle = sortTitle,
        monitored = true,
        images = images,
        remotePoster = remotePoster,
    )
}
