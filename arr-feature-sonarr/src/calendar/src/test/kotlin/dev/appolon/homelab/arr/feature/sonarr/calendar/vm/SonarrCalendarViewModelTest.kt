package dev.appolon.homelab.arr.feature.sonarr.calendar.vm

import dev.appolon.homelab.arr.api.sonarr.CalendarApi
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.calendar.FakeCalendarEntityStore
import dev.appolon.homelab.arr.feature.sonarr.calendar.data.SonarrCalendarRepository
import dev.appolon.homelab.arr.feature.sonarr.calendar.data.SonarrCalendarWindow
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

class SonarrCalendarViewModelTest {

    private val serverId = ServerId("s1")
    private val today = LocalDate.of(2026, 10, 20)
    private val main = UnconfinedTestDispatcher()
    private lateinit var fake: FakeSonarrCalendar
    private lateinit var store: FakeCalendarEntityStore
    private lateinit var repository: SonarrCalendarRepository
    private var clock: Long = 1_000_000L

    /** Wraps the fake so tests can count fetches without touching store state. */
    private class RecordingApi(private val delegate: CalendarApi) : CalendarApi {
        var calls = 0

        override suspend fun list(
            @Query("start") start: String?,
            @Query("end") end: String?,
            @Query("unmonitored") unmonitored: Boolean?,
            @Query("includeSeries") includeSeries: Boolean?,
            @Query("includeEpisodeFile") includeEpisodeFile: Boolean?,
            @Query("includeEpisodeImages") includeEpisodeImages: Boolean?,
            @Query("tags") tags: String?,
        ): List<EpisodeResource> {
            calls++
            return delegate.list(start, end, unmonitored, includeSeries, includeEpisodeFile, includeEpisodeImages, tags)
        }

        override suspend fun get(id: Int) = delegate.get(id)
    }

    private lateinit var api: RecordingApi

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        fake = FakeSonarrCalendar()
        api = RecordingApi(fake.calendar)
        store = FakeCalendarEntityStore()
        repository = SonarrCalendarRepository(
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
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        val vm = viewModel()

        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        assertEquals(listOf(1), vm.state.value.entries.map { it.episodeId })
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
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        val vm = viewModel()
        vm.nextMonth()

        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
        // November was already prefetched, so only December warms up now.
        assertEquals(4, api.calls)
    }

    @Test
    fun `goToToday returns to the current month and selects today`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        val vm = viewModel()
        vm.nextMonth()
        vm.nextMonth()
        vm.goToToday()

        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        assertEquals(today, vm.state.value.selectedDate)
    }

    @Test
    fun `unmonitored toggle purges the cache and refetches with the new filter`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05", monitored = true)
        fake.calendarStore[2] = episode(2, airDate = "2026-10-06", monitored = false)
        val vm = viewModel()
        assertEquals(listOf(1), vm.state.value.entries.map { it.episodeId })

        vm.setIncludeUnmonitored(true)

        assertEquals(setOf(1, 2), vm.state.value.entries.map { it.episodeId }.toSet())
        assertTrue(vm.state.value.includeUnmonitored)
        val windows = store.getAll(
            serverId,
            SonarrCalendarRepository.WINDOW_TYPE,
            SonarrCalendarWindow.serializer(),
        )
        assertTrue(windows.all { it.unmonitored })
        assertTrue(windows.isNotEmpty())
    }

    @Test
    fun `calendar changed signal refreshes the visible month`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        val events = MutableSharedFlow<SonarrCalendarRefreshSignal>(extraBufferCapacity = 8)
        val vm = viewModel(events)
        val callsAfterInit = api.calls

        events.tryEmit(SonarrCalendarRefreshSignal(serverId))
        advanceTimeBy(500)

        // Only the visible month refetches; the neighbors are still fresh.
        assertEquals(callsAfterInit + 1, api.calls)
    }

    @Test
    fun `calendar changed signal for another server is ignored`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        val events = MutableSharedFlow<SonarrCalendarRefreshSignal>(extraBufferCapacity = 8)
        viewModel(events)
        val callsAfterInit = api.calls

        events.tryEmit(SonarrCalendarRefreshSignal(ServerId("other")))
        advanceTimeBy(500)

        assertEquals(callsAfterInit, api.calls)
    }

    @Test
    fun `deep link selects the day and exposes its episodes`() = runTest {
        fake.calendarStore[1] = episode(1, seriesId = 1, airDate = "2026-10-05", sortTitle = "alpha")
        fake.calendarStore[2] = episode(2, seriesId = 2, airDate = "2026-10-05", sortTitle = "beta")
        val vm = viewModel(initialSelectedDate = LocalDate.of(2026, 10, 5))

        assertEquals(LocalDate.of(2026, 10, 5), vm.state.value.selectedDate)
        assertEquals(
            listOf("alpha", "beta"),
            vm.state.value.selectedDayEntries?.map { it.sortTitle },
        )
    }

    @Test
    fun `month cells highlight today and group entries per day`() = runTest {
        fake.calendarStore[1] = episode(1, seriesId = 1, airDate = today.toString())
        fake.calendarStore[2] = episode(2, seriesId = 2, airDate = "2026-10-27")
        val vm = viewModel()

        val cells = vm.state.value.monthCells
        assertEquals(1, cells.count { it.isToday })
        assertEquals(today, cells.first { it.isToday }.date)
        assertEquals(listOf(1), cells.first { it.isToday }.entries.map { it.episodeId })
        assertEquals(2, vm.state.value.upcomingCount)
    }

    @Test
    fun `agenda view lists days from today forward`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-02")
        fake.calendarStore[2] = episode(2, airDate = "2026-10-21")
        fake.calendarStore[3] = episode(3, airDate = "2026-11-14")
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
        val collected = mutableListOf<LceState<SonarrCalendarUiState>>()
        backgroundScope.launch(main) { vm.lce.toList(collected) }

        assertTrue(vm.state.value.loaded)
        assertEquals(LceState.Empty, collected.last())
    }

    @Test
    fun `lce reports error when the first fetch fails with an empty cache`() = runTest {
        fake.error = { if (it == "Calendar.list") IOException("offline") else null }
        val vm = viewModel()
        val collected = mutableListOf<LceState<SonarrCalendarUiState>>()
        backgroundScope.launch(main) { vm.lce.toList(collected) }

        assertTrue(collected.last() is LceState.Error)
    }

    @Test
    fun `lce keeps cached content when a later refresh fails`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        val vm = viewModel()
        val collected = mutableListOf<LceState<SonarrCalendarUiState>>()
        backgroundScope.launch(main) { vm.lce.toList(collected) }

        fake.error = { if (it == "Calendar.list") IOException("offline") else null }
        vm.refresh()

        val last = collected.last()
        assertTrue(last is LceState.Content)
        assertEquals(1, (last as LceState.Content).data.entries.size)
    }

    private fun viewModel(
        events: MutableSharedFlow<SonarrCalendarRefreshSignal> = MutableSharedFlow(extraBufferCapacity = 8),
        initialSelectedDate: LocalDate? = null,
    ): SonarrCalendarViewModel = SonarrCalendarViewModel(
        repository = repository,
        events = events,
        today = { today },
        initialSelectedDate = initialSelectedDate,
        firstDay = DayOfWeek.SUNDAY,
    )

    private fun episode(
        id: Int,
        airDate: String,
        seriesId: Int = 1,
        sortTitle: String = "Series $seriesId",
        monitored: Boolean = true,
    ): EpisodeResource = EpisodeResource(
        id = id,
        seriesId = seriesId,
        seasonNumber = 1,
        episodeNumber = id,
        title = "Episode $id",
        airDate = airDate,
        monitored = monitored,
        series = SeriesResource(id = seriesId, title = sortTitle, sortTitle = sortTitle, monitored = true),
    )
}
