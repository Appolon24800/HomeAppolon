package dev.appolon.homelab.arr.feature.sonarr.calendar.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.calendar.data.SonarrCalendarEntry
import dev.appolon.homelab.arr.feature.sonarr.calendar.data.SonarrCalendarRepository
import dev.appolon.homelab.arr.feature.sonarr.calendar.data.countUpcoming
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which layout the calendar renders. */
enum class CalendarViewMode { MONTH, AGENDA }

/**
 * Live refresh signal for the calendar. `:arr-sync` is not a dependency of
 * this module, so the integration layer maps
 * `ArrEvent.CalendarChanged(serverId)` onto this type and injects the flow;
 * tests inject a mutable flow directly.
 */
data class SonarrCalendarRefreshSignal(val serverId: ServerId)

/**
 * State of the Sonarr calendar screen. Everything the composables render
 * derives from here; month cells, agenda rows, and the upcoming badge are
 * computed lazily so equality checks stay cheap.
 */
data class SonarrCalendarUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val entries: List<SonarrCalendarEntry> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val includeUnmonitored: Boolean = false,
    val selectedDate: LocalDate? = null,
    val refreshing: Boolean = false,
    val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
    /** Months whose window has been ensured this session; drives prefetch assertions. */
    val ensuredMonths: Set<YearMonth> = emptySet(),
) {

    /** Cells of the visible month grid, first-day-of-week aligned. */
    val monthCells: List<CalendarDay> by lazy(LazyThreadSafetyMode.NONE) {
        monthCellsOf(month, entries, today, firstDayOfWeek)
    }

    /** Agenda rows from the anchor (today inside the current month, else the month's first day). */
    val agendaDays: List<CalendarDay> by lazy(LazyThreadSafetyMode.NONE) {
        val anchor = if (month == YearMonth.from(today)) today else month.atDay(1)
        agendaDaysOf(entries, anchor, today)
    }

    /** Entries of the selected day, when one is selected. */
    val selectedDayEntries: List<SonarrCalendarEntry>? by lazy(LazyThreadSafetyMode.NONE) {
        selectedDate?.let { date -> entries.filter { it.airDate == date.toString() }.sorted() }
    }

    /** Distinct series with a monitored episode airing within the next week; the shell badge number. */
    val upcomingCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        countUpcoming(entries, today, horizonDays = 7)
    }

    /** True when the visible month carries no entries at all. */
    val monthIsEmpty: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        entries.none { YearMonth.from(LocalDate.parse(it.airDate)) == month }
    }
}

/**
 * ViewModel of the Sonarr episode calendar. Owns no Android types so unit
 * tests construct it against fakes. Month navigation is cache-first with
 * adjacent months prefetched into the entity store, the unmonitored toggle
 * purges and refetches, and [events] drives live refresh with a short debounce
 * so bursts of `CalendarChanged` signals collapse into one fetch. Navigation
 * always lands on the requested month: with a stale network the grid renders
 * whatever the cache holds for that window.
 */
@OptIn(FlowPreview::class)
class SonarrCalendarViewModel(
    private val repository: SonarrCalendarRepository,
    private val events: Flow<SonarrCalendarRefreshSignal> = emptyFlow(),
    today: () -> LocalDate = LocalDate::now,
    initialMonth: YearMonth? = null,
    initialSelectedDate: LocalDate? = null,
    initialIncludeUnmonitored: Boolean = false,
    firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
) : ViewModel() {

    private val todayDate: LocalDate = today()

    private val _state = MutableStateFlow(
        SonarrCalendarUiState(
            today = todayDate,
            month = initialMonth ?: YearMonth.from(todayDate),
            selectedDate = initialSelectedDate,
            includeUnmonitored = initialIncludeUnmonitored,
            firstDayOfWeek = firstDay,
        ),
    )
    val state: StateFlow<SonarrCalendarUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SonarrCalendarUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.entries.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.entries.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeEntries()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { entries -> _state.update { it.copy(entries = entries, loaded = true) } }
        }
        showMonth(_state.value.month)
        viewModelScope.launch {
            events
                .filter { it.serverId == repository.serverId }
                .debounce(300)
                .collect { refresh() }
        }
    }

    fun previousMonth() = showMonth(_state.value.month.minusMonths(1))

    fun nextMonth() = showMonth(_state.value.month.plusMonths(1))

    /** Returns to the current month and selects today, ready for a notification deep link. */
    fun goToToday() {
        val thisMonth = YearMonth.from(todayDate)
        _state.update { it.copy(selectedDate = todayDate) }
        if (_state.value.month != thisMonth) {
            showMonth(thisMonth)
        }
    }

    fun setViewMode(mode: CalendarViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    /** Toggles unmonitored visibility; the cache is purged because the server-side filter changed. */
    fun setIncludeUnmonitored(include: Boolean) {
        val current = _state.value
        if (current.includeUnmonitored == include) return
        _state.update { it.copy(includeUnmonitored = include, refreshing = true) }
        viewModelScope.launch {
            repository.purgeEntries()
            loadMonth(current.month, force = true)
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun selectDate(date: LocalDate?) {
        _state.update { it.copy(selectedDate = date) }
    }

    /** Pull-to-refresh and live refresh: forces the visible month and re-warms its neighbors. */
    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            loadMonth(_state.value.month, force = true)
            _state.update { it.copy(refreshing = false) }
        }
    }

    /**
     * Cache-first navigation into [month]: fetches only a stale or missing
     * window, then warms the adjacent months in the background so swiping
     * lands on data already in the store.
     */
    private fun showMonth(month: YearMonth) {
        viewModelScope.launch { loadMonth(month, force = false) }
    }

    private suspend fun loadMonth(month: YearMonth, force: Boolean) {
        _state.update { it.copy(month = month) }
        val unmonitored = _state.value.includeUnmonitored
        val visible: ArrResult<Unit> = if (force) {
            repository.refreshMonth(month, unmonitored)
        } else {
            repository.ensureMonth(month, unmonitored)
        }
        _state.update { state ->
            state.copy(
                loaded = true,
                loadError = (visible as? ArrResult.Err)?.error,
                ensuredMonths = state.ensuredMonths + month,
            )
        }
        val neighbors = listOf(month.minusMonths(1), month.plusMonths(1))
        neighbors.forEach { neighbor ->
            val result = repository.ensureMonth(neighbor, unmonitored)
            if (result is ArrResult.Ok) {
                _state.update { it.copy(ensuredMonths = it.ensuredMonths + neighbor) }
            }
        }
    }
}
