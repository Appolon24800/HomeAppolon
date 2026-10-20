package dev.appolon.homelab.arr.feature.radarr.calendar.vm

import dev.appolon.homelab.arr.feature.radarr.calendar.data.CalendarEventKind
import dev.appolon.homelab.arr.feature.radarr.calendar.data.RadarrCalendarEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarrCalendarPresentationTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun entry(movieId: Int, date: String, title: String = "Movie $movieId"): RadarrCalendarEntry =
        RadarrCalendarEntry(
            movieId = movieId,
            title = title,
            eventDate = date,
            eventKind = CalendarEventKind.RELEASE,
        )

    @Test
    fun `month cells align to the first day of week and pad overflow days`() {
        val cells = monthCellsOf(YearMonth.of(2026, 10), emptyList(), today, DayOfWeek.SUNDAY)
        assertEquals(35, cells.size)
        assertEquals(LocalDate.of(2026, 9, 27), cells.first().date)
        assertEquals(LocalDate.of(2026, 10, 31), cells.last().date)
        assertEquals(4, cells.count { !it.inMonth })
        assertTrue(cells.take(4).none { it.isToday })
    }

    @Test
    fun `cells mark today only on the matching date`() {
        val cells = monthCellsOf(YearMonth.of(2026, 10), emptyList(), today, DayOfWeek.MONDAY)
        val todayCells = cells.filter { it.isToday }
        assertEquals(1, todayCells.size)
        assertEquals(today, todayCells.single().date)
    }

    @Test
    fun `december grid crosses the year boundary without leaking december of another year`() {
        val cells = monthCellsOf(YearMonth.of(2026, 12), emptyList(), today, DayOfWeek.SUNDAY)
        assertEquals(35, cells.size)
        assertEquals(LocalDate.of(2026, 11, 29), cells.first().date)
        assertEquals(LocalDate.of(2027, 1, 2), cells.last().date)
        assertFalse(cells.first().inMonth)
        assertFalse(cells.last().inMonth)
    }

    @Test
    fun `entries attach by date and sort by title`() {
        val entries = listOf(
            entry(2, "2026-10-21", "Zeta"),
            entry(1, "2026-10-21", "Alpha"),
            entry(3, "2026-10-22", "Middle"),
        )
        val cells = monthCellsOf(YearMonth.of(2026, 10), entries, today, DayOfWeek.SUNDAY)
        val twentyFirst = cells.first { it.date == LocalDate.of(2026, 10, 21) }
        assertEquals(listOf("Alpha", "Zeta"), twentyFirst.entries.map { it.title })
        val twentySecond = cells.first { it.date == LocalDate.of(2026, 10, 22) }
        assertEquals(listOf("Middle"), twentySecond.entries.map { it.title })
    }

    @Test
    fun `agenda drops past days, sorts ascending, and caps the day count`() {
        val entries = (1..80).map { index ->
            entry(index, LocalDate.of(2026, 10, 1).plusDays(index.toLong()).toString())
        }
        val agenda = agendaDaysOf(entries, anchor = today, today = today, limit = 60)
        // 61 candidate days from today onward (Oct 21 through Dec 20), capped at 60.
        assertEquals(60, agenda.size)
        assertEquals(LocalDate.of(2026, 10, 21), agenda.first().date)
        assertEquals(LocalDate.of(2026, 12, 19), agenda.last().date)
        assertTrue(agenda.zipWithNext().all { (earlier, later) -> earlier.date < later.date })
        assertTrue(agenda.none { it.date < today })
    }

    @Test
    fun `agenda anchors outside the current month include the whole anchor month`() {
        val entries = listOf(
            entry(1, "2026-10-21"),
            entry(2, "2026-12-05"),
            entry(3, "2026-12-24"),
        )
        val agenda = agendaDaysOf(entries, anchor = LocalDate.of(2026, 12, 1), today = today)
        assertEquals(listOf(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 24)), agenda.map { it.date })
        assertTrue(agenda.none { it.isToday })
    }
}
