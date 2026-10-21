package dev.appolon.homelab.arr.feature.radarr.calendar.vm

import dev.appolon.homelab.arr.api.radarr.CalendarApi
import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.calendar.FakeCalendarEntityStore
import dev.appolon.homelab.arr.feature.radarr.calendar.data.RadarrCalendarRepository
import dev.appolon.homelab.arr.feature.radarr.calendar.data.RadarrCalendarWindow
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.http.Query

class RadarrCalendarViewModelTest {

    private val serverId = ServerId("s1")
    private val today = LocalDate.of(2026, 10, 20)
    private val main = UnconfinedTestDispatcher()
    private lateinit var fake: FakeRadarrCalendar
    private lateinit var store: FakeCalendarEntityStore
    private lateinit var repository: RadarrCalendarRepository
    private var clock: Long = 1_000_000L

    /** Wraps the fake so tests can count fetches without touching store state. */
    private class RecordingApi(private val delegate: CalendarApi) : CalendarApi {
        var calls = 0

        override suspend fun calendar(
            @Query("start") start: String?,
            @Query("end") end: String?,
            @Query("unmonitored") unmonitored: Boolean?,
            @Query("tags") tags: String?,
        ): List<MovieResource> {
            calls++
            return delegate.calendar(start, end, unmonitored, tags)
        }
    }

    private lateinit var api: RecordingApi

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        fake = FakeRadarrCalendar()
        api = RecordingApi(fake)
        store = FakeCalendarEntityStore()
        repository = RadarrCalendarRepository(
            serverId = serverId,
            store = store,
            calendarApi = api,
            now = { clock },
            io = main,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load fetches the visible month and prefetches its neighbors`() = runTest {
        fake.movies += movie(1, "Alpha", "2026-10-05T00:00:00Z")
        val vm = viewModel()

        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        assertEquals(listOf(1), vm.state.value.entries.map { it.movieId })
        assertTrue(vm.state.value.loaded)
        assertEquals(
            setOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)),
            vm.state.value.ensuredMonths,
        )
        // One fetch per window: previous, visible, next.
        assertEquals(3, api.calls)
    }

    @Test
    fun `month navigation stays cache-first while windows are fresh`() = runTest {
        fake.movies += movie(1, "Alpha", "2026-10-05T00:00:00Z")
        val vm = viewModel()
        vm.nextMonth()

        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
        // November was already prefetched, so only December warms up now.
        assertEquals(4, api.calls)
    }

    @Test
    fun `goToToday returns to the current month and selects today`() = runTest {
        fake.movies += movie(1, "Alpha", "2026-10-05T00:00:00Z")
        val vm = viewModel()
        vm.nextMonth()
        vm.nextMonth()
        vm.goToToday()

        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        assertEquals(today, vm.state.value.selectedDate)
    }

    @Test
    fun `unmonitored toggle purges the cache and refetches with the new filter`() = runTest {
        fake.movies += movie(1, "Alpha", "2026-10-05T00:00:00Z", monitored = true)
        fake.movies += movie(2, "Ghost", "2026-10-06T00:00:00Z", monitored = false)
        val vm = viewModel()
        assertEquals(listOf(1), vm.state.value.entries.map { it.movieId })

        vm.setIncludeUnmonitored(true)

        assertEquals(setOf(1, 2), vm.state.value.entries.map { it.movieId }.toSet())
        assertTrue(vm.state.value.includeUnmonitored)
        val windows = store.getAll(
            serverId,
            RadarrCalendarRepository.WINDOW_TYPE,
            RadarrCalendarWindow.serializer(),
        )
        assertTrue(windows.all { it.unmonitored })
        assertTrue(windows.isNotEmpty())
    }

    @Test
    fun `calendar changed signal refreshes the visible month`() = runTest {
        fake.movies += movie(1, "Alpha", "2026-10-05T00:00:00Z")
        val events = MutableSharedFlow<CalendarRefreshSignal>(extraBufferCapacity = 8)
        val vm = viewModel(events)
        val callsAfterInit = api.calls

        events.tryEmit(CalendarRefreshSignal(serverId))
        advanceTimeBy(500)

        // Only the visible month refetches; the neighbors are still fresh.
        assertEquals(callsAfterInit + 1, api.calls)
    }

    @Test
    fun `calendar changed signal for another server is ignored`() = runTest {
        fake.movies += movie(1, "Alpha", "2026-10-05T00:00:00Z")
        val events = MutableSharedFlow<CalendarRefreshSignal>(extraBufferCapacity = 8)
        viewModel(events)
        val callsAfterInit = api.calls

        events.tryEmit(CalendarRefreshSignal(ServerId("other")))
        advanceTimeBy(500)

        assertEquals(callsAfterInit, api.calls)
    }

    @Test
    fun `deep link selects the day and exposes its entries`() = runTest {
        fake.movies += movie(1, "Alpha", "2026-10-05T00:00:00Z")
        fake.movies += movie(2, "Beta", "2026-10-05T00:00:00Z")
        val vm = viewModel(initialSelectedDate = LocalDate.of(2026, 10, 5))

        assertEquals(LocalDate.of(2026, 10, 5), vm.state.value.selectedDate)
        assertEquals(listOf("Alpha", "Beta"), vm.state.value.selectedDayEntries?.map { it.title })
    }

    @Test
    fun `month cells highlight today and group entries per day`() = runTest {
        fake.movies += movie(1, "Alpha", today.toString() + "T00:00:00Z")
        fake.movies += movie(2, "Beta", "2026-10-27T00:00:00Z")
        val vm = viewModel()

        val cells = vm.state.value.monthCells
        assertEquals(1, cells.count { it.isToday })
        assertEquals(today, cells.first { it.isToday }.date)
        assertEquals(listOf("Alpha"), cells.first { it.isToday }.entries.map { it.title })
        assertEquals(2, vm.state.value.upcomingCount)
    }

    @Test
    fun `agenda view lists days from today forward`() = runTest {
        fake.movies += movie(1, "Past", "2026-10-02T00:00:00Z")
        fake.movies += movie(2, "Soon", "2026-10-21T00:00:00Z")
        fake.movies += movie(3, "Later", "2026-11-14T00:00:00Z")
        val vm = viewModel()

        vm.setViewMode(CalendarViewMode.AGENDA)

        assertEquals(CalendarViewMode.AGENDA, vm.state.value.viewMode)
        assertEquals(
            listOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 11, 14)),
            vm.state.value.agendaDays.map { it.date },
        )
    }

    @Test
    fun `lce reports empty when the calendar has no entries`() = runTest {
        val vm = viewModel()
        val collected = mutableListOf<LceState<RadarrCalendarUiState>>()
        backgroundScope.launch(main) { vm.lce.toList(collected) }

        assertTrue(vm.state.value.loaded)
        assertEquals(LceState.Empty, collected.last())
    }

    @Test
    fun `lce reports error when the first fetch fails with an empty cache`() = runTest {
        fake.errorFor = { if (it == "calendar") IOException("offline") else null }
        val vm = viewModel()
        val collected = mutableListOf<LceState<RadarrCalendarUiState>>()
        backgroundScope.launch(main) { vm.lce.toList(collected) }

        assertTrue(collected.last() is LceState.Error)
    }

    @Test
    fun `lce keeps cached content when a later refresh fails`() = runTest {
        fake.movies += movie(1, "Alpha", "2026-10-05T00:00:00Z")
        val vm = viewModel()
        val collected = mutableListOf<LceState<RadarrCalendarUiState>>()
        backgroundScope.launch(main) { vm.lce.toList(collected) }

        fake.errorFor = { if (it == "calendar") IOException("offline") else null }
        vm.refresh()

        val last = collected.last()
        assertTrue(last is LceState.Content)
        assertEquals(1, (last as LceState.Content).data.entries.size)
    }

    private fun viewModel(
        events: MutableSharedFlow<CalendarRefreshSignal> = MutableSharedFlow(extraBufferCapacity = 8),
        initialSelectedDate: LocalDate? = null,
    ): RadarrCalendarViewModel = RadarrCalendarViewModel(
        repository = repository,
        events = events,
        today = { today },
        initialSelectedDate = initialSelectedDate,
        firstDay = DayOfWeek.SUNDAY,
    )

    private fun movie(id: Int, title: String, date: String, monitored: Boolean = true): MovieResource =
        MovieResource(
            id = id,
            title = title,
            monitored = monitored,
            physicalRelease = date,
        )
}
