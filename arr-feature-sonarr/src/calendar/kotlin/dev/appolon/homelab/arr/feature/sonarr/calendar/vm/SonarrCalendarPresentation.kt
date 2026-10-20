package dev.appolon.homelab.arr.feature.sonarr.calendar.vm

import dev.appolon.homelab.arr.feature.sonarr.calendar.data.SonarrCalendarEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * One day cell of the calendar, used by both views: the month grid renders
 * every cell (with [inMonth] dimming overflow days), the agenda renders only
 * days that carry entries starting at its anchor.
 */
data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean = true,
    val isToday: Boolean = false,
    val entries: List<SonarrCalendarEntry> = emptyList(),
)

/**
 * Cells of one month grid: [firstDayOfWeek]-aligned, padded with the previous
 * and next month's days so every row holds seven cells. Entries are attached
 * by air date and sorted for stable ordering.
 */
fun monthCellsOf(
    month: YearMonth,
    entries: List<SonarrCalendarEntry>,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
): List<CalendarDay> {
    val byDate = entries.groupBy { it.airDate }
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val weeks = (leading + month.lengthOfMonth() + 6) / 7
    val gridStart = first.minusDays(leading.toLong())
    return (0 until weeks * 7).map { index ->
        val date = gridStart.plusDays(index.toLong())
        CalendarDay(
            date = date,
            inMonth = YearMonth.from(date) == month,
            isToday = date == today,
            entries = (byDate[date.toString()] ?: emptyList()).sorted(),
        )
    }
}

/**
 * Agenda rows: days with at least one entry from [anchor] onward, capped at
 * [limit] days so an unbounded cache cannot produce an endless list.
 */
fun agendaDaysOf(
    entries: List<SonarrCalendarEntry>,
    anchor: LocalDate,
    today: LocalDate,
    limit: Int = 60,
): List<CalendarDay> = entries.asSequence()
    .mapNotNull { entry -> runCatching { LocalDate.parse(entry.airDate) }.getOrNull()?.let { it to entry } }
    .filter { (date, _) -> date >= anchor }
    .groupBy({ (date, _) -> date }, { (_, entry) -> entry })
    .toSortedMap()
    .entries.take(limit)
    .map { (date, dayEntries) ->
        CalendarDay(date = date, isToday = date == today, entries = dayEntries.sorted())
    }
