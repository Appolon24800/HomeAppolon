package dev.appolon.homelab.arr.feature.sonarr.calendar.vm

import dev.appolon.homelab.arr.feature.sonarr.calendar.data.SonarrCalendarEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class SonarrCalendarPresentationTest {

    private val today = LocalDate.of(2026, 10, 20)

    private fun entry(episodeId: Int, airDate: String, sortTitle: String = "s"): SonarrCalendarEntry =
        SonarrCalendarEntry(
            episodeId = episodeId,
            seriesId = 1,
            sortTitle = sortTitle,
            airDate = airDate,
        )

    @Test
    fun `monthCells align to the first day of week and pad full weeks`() {
        val month = YearMonth.of(2026, 10) // Oct 1, 2026 is a Thursday.
        val cells = monthCellsOf(month, emptyList(), today, DayOfWeek.SUNDAY)

        assertEquals(35, cells.size)
        assertEquals(LocalDate.of(2026, 9, 27), cells.first().date)
        assertEquals(LocalDate.of(2026, 10, 31), cells.last().date)
        assertEquals(4, cells.count { !it.inMonth })
        assertEquals(DayOfWeek.SUNDAY, cells.first().date.dayOfWeek)
    }

    @Test
    fun `monthCells highlight today once and group entries per air date`() {
        val month = YearMonth.of(2026, 10)
        val entries = listOf(
            entry(1, today.toString()),
            entry(2, today.toString()),
            entry(3, "2026-10-27"),
        )

        val cells = monthCellsOf(month, entries, today, DayOfWeek.SUNDAY)

        assertEquals(1, cells.count { it.isToday })
        val todayCell = cells.first { it.isToday }
        assertEquals(listOf(1, 2), todayCell.entries.map { it.episodeId })
        assertEquals(listOf(3), cells.first { it.date == LocalDate.of(2026, 10, 27) }.entries.map { it.episodeId })
    }

    @Test
    fun `monthCells sort a day's entries by sort title`() {
        val month = YearMonth.of(2026, 10)
        val entries = listOf(
            entry(1, "2026-10-05", sortTitle = "zeta"),
            entry(2, "2026-10-05", sortTitle = "alpha"),
        )

        val cells = monthCellsOf(month, entries, today, DayOfWeek.SUNDAY)

        assertEquals(
            listOf("alpha", "zeta"),
            cells.first { it.date == LocalDate.of(2026, 10, 5) }.entries.map { it.sortTitle },
        )
    }

    @Test
    fun `agendaDays lists days with entries from the anchor forward and caps the list`() {
        val anchor = LocalDate.of(2026, 10, 20)
        val entries = (0 until 90).map { index -> entry(index, anchor.plusDays(index.toLong()).toString()) }

        val days = agendaDaysOf(entries, anchor, today)

        assertEquals(60, days.size)
        assertEquals(anchor, days.first().date)
        assertEquals(anchor.plusDays(59), days.last().date)
        assertEquals(1, days.first().entries.size)
        assertEquals(1, days.count { it.isToday })
    }

    @Test
    fun `agendaDays skips days before the anchor and empty days`() {
        val anchor = LocalDate.of(2026, 10, 20)
        val entries = listOf(
            entry(1, "2026-10-19"),
            entry(2, "2026-10-21"),
            entry(3, "2026-11-14"),
        )

        val days = agendaDaysOf(entries, anchor, today)

        assertEquals(
            listOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 11, 14)),
            days.map { it.date },
        )
    }
}
