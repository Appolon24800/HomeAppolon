package dev.appolon.homelab.arr.feature.lidarr.calendar.vm

import dev.appolon.homelab.arr.feature.lidarr.calendar.data.LidarrCalendarEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LidarrCalendarPresentationTest {

    @Test
    fun `monthCells aligns the grid to the first day of week and attaches entries by date`() {
        val month = YearMonth.of(2026, 10) // 2026-10-01 is a Thursday.
        val entries = listOf(
            LidarrCalendarEntry(albumId = 1, artistId = 1, releaseDate = "2026-10-01"),
            LidarrCalendarEntry(albumId = 2, artistId = 1, releaseDate = "2026-10-01"),
            LidarrCalendarEntry(albumId = 3, artistId = 2, releaseDate = "2026-10-31"),
        )

        val cells = monthCellsOf(month, entries, today = LocalDate.of(2026, 10, 20), DayOfWeek.MONDAY)

        // Five weeks: three leading September days plus 31 October days.
        assertEquals(35, cells.size)
        assertEquals(DayOfWeek.MONDAY, cells.first().date.dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 28), cells.first().date)
        assertEquals(2, cells.first { it.date == LocalDate.of(2026, 10, 1) }.entries.size)
        assertEquals(1, cells.first { it.date == LocalDate.of(2026, 10, 31) }.entries.size)
        assertTrue(cells.first { it.date == LocalDate.of(2026, 10, 20) }.isToday)
        assertFalse(cells.first().inMonth)
        assertTrue(cells.first { it.date == LocalDate.of(2026, 10, 15) }.inMonth)
    }

    @Test
    fun `monthCells sorts each day by artist sort key`() {
        val month = YearMonth.of(2026, 10)
        val entries = listOf(
            LidarrCalendarEntry(albumId = 1, artistId = 1, sortTitle = "zebra", releaseDate = "2026-10-02"),
            LidarrCalendarEntry(albumId = 2, artistId = 2, sortTitle = "ant", releaseDate = "2026-10-02"),
        )

        val day = monthCellsOf(month, entries, LocalDate.of(2026, 10, 1), DayOfWeek.SUNDAY)
            .first { it.date == LocalDate.of(2026, 10, 2) }

        assertEquals(listOf(2, 1), day.entries.map { it.albumId })
    }

    @Test
    fun `agendaDaysOf starts at the anchor, groups per day, and caps the range`() {
        val anchor = LocalDate.of(2026, 10, 20)
        val entries = (0 until 80).map { index ->
            LidarrCalendarEntry(
                albumId = index,
                artistId = 1,
                releaseDate = anchor.plusDays(index.toLong()).toString(),
            )
        } + LidarrCalendarEntry(albumId = 100, artistId = 1, releaseDate = "2026-10-01")

        val days = agendaDaysOf(entries, anchor, today = anchor, limit = 60)

        assertEquals(60, days.size)
        assertEquals(anchor, days.first().date)
        assertTrue(days.first().isToday)
        // The pre-anchor entry never shows up.
        assertTrue(days.none { it.date == LocalDate.of(2026, 10, 1) })
    }

    @Test
    fun `agendaDaysOf skips days without entries`() {
        val anchor = LocalDate.of(2026, 10, 20)
        val entries = listOf(
            LidarrCalendarEntry(albumId = 1, artistId = 1, releaseDate = "2026-10-21"),
            LidarrCalendarEntry(albumId = 2, artistId = 2, releaseDate = "2026-10-25"),
        )

        val days = agendaDaysOf(entries, anchor, anchor)

        assertEquals(listOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 25)), days.map { it.date })
    }
}
