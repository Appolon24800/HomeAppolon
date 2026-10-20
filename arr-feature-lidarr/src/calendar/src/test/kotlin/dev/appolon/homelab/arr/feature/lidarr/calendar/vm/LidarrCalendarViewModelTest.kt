package dev.appolon.homelab.arr.feature.lidarr.calendar.vm

import dev.appolon.homelab.arr.api.lidarr.CalendarApi
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.calendar.FakeCalendarEntityStore
import dev.appolon.homelab.arr.feature.lidarr.calendar.data.LidarrCalendarRepository
import dev.appolon.homelab.arr.feature.lidarr.calendar.data.LidarrCalendarWindow
import dev.appolon.homelab.arr.ui.lce.LceState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LidarrCalendarViewModelTest {

    private val serverId = ServerId("s1")
    private val today = LocalDate.of(2026, 10, 20)
    private val main = UnconfinedTestDispatcher()
    private lateinit var fake: FakeLidarrCalendar
    private lateinit var store: FakeCalendarEntityStore
    private lateinit var repository: LidarrCalendarRepository
    private var clock: Long = 1_000_000L

    /** Wraps the fake so tests can count fetches without touching store state. */
    private class RecordingApi(private val delegate: CalendarApi) : CalendarApi {
        var calls = 0

        override suspend fun getCalendar(
            @Query("start") start: String?,
            @Query("end") end: String?,
            @Query("unmonitored") unmonitored: Boolean?,
            @Query("includeArtist") includeArtist: Boolean?,
            @Query("tags") tags: String?,
        ): List<AlbumResource> {
            calls++
            return delegate.getCalendar(start, end, unmonitored, includeArtist, tags)
        }

        override suspend fun getCalendarById(id: Int): AlbumResource = delegate.getCalendarById(id)
    }

    private lateinit var api: RecordingApi

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        fake = FakeLidarrCalendar()
        api = RecordingApi(fake)
        store = FakeCalendarEntityStore()
        repository = LidarrCalendarRepository(
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
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        val vm = viewModel()

        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        assertEquals(listOf(1), vm.state.value.entries.map { it.albumId })
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
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        val vm = viewModel()
        vm.nextMonth()

        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
        // November was already prefetched, so only December warms up now.
        assertEquals(4, api.calls)
    }

    @Test
    fun `goToToday returns to the current month and selects today`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        val vm = viewModel()
        vm.nextMonth()
        vm.nextMonth()
        vm.goToToday()

        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        assertEquals(today, vm.state.value.selectedDate)
    }

    @Test
    fun `unmonitored toggle purges the cache and refetches with the new filter`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05", monitored = true)
        fake.calendar[2] = album(2, releaseDate = "2026-10-06", monitored = false)
        val vm = viewModel()
        assertEquals(listOf(1), vm.state.value.entries.map { it.albumId })

        vm.setIncludeUnmonitored(true)

        assertEquals(setOf(1, 2), vm.state.value.entries.map { it.albumId }.toSet())
        assertTrue(vm.state.value.includeUnmonitored)
        val windows = store.getAll(
            serverId,
            LidarrCalendarRepository.WINDOW_TYPE,
            LidarrCalendarWindow.serializer(),
        )
        assertTrue(windows.all { it.unmonitored })
        assertTrue(windows.isNotEmpty())
    }

    @Test
    fun `calendar changed signal refreshes the visible month`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        val events = MutableSharedFlow<LidarrCalendarRefreshSignal>(extraBufferCapacity = 8)
        val vm = viewModel(events)
        val callsAfterInit = api.calls

        events.tryEmit(LidarrCalendarRefreshSignal(serverId))
        advanceTimeBy(500)

        // Only the visible month refetches; the neighbors are still fresh.
        assertEquals(callsAfterInit + 1, api.calls)
    }

    @Test
    fun `signals for other servers never refresh`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        val events = MutableSharedFlow<LidarrCalendarRefreshSignal>(extraBufferCapacity = 8)
        val vm = viewModel(events)
        val callsAfterInit = api.calls

        events.tryEmit(LidarrCalendarRefreshSignal(ServerId("other")))
        advanceTimeBy(500)

        assertEquals(callsAfterInit, api.calls)
    }

    @Test
    fun `lce renders content once entries arrive and stays content on refresh errors`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        val vm = viewModel()
        val collected = mutableListOf<LceState<LidarrCalendarUiState>>()
        backgroundScope.launch(main) { vm.lce.toList(collected) }
        assertTrue(collected.last() is LceState.Content)

        fake.error = { if (it == "getCalendar") java.io.IOException("offline") else null }
        vm.refresh()

        val last = collected.last()
        assertTrue(last is LceState.Content)
        assertEquals(1, (last as LceState.Content).data.entries.size)
    }

    @Test
    fun `lce reports empty when the calendar has no entries`() = runTest {
        val vm = viewModel()
        val collected = mutableListOf<LceState<LidarrCalendarUiState>>()
        backgroundScope.launch(main) { vm.lce.toList(collected) }

        assertTrue(vm.state.value.loaded)
        assertEquals(LceState.Empty, collected.last())
    }

    @Test
    fun `lce reports error when the first fetch fails with an empty cache`() = runTest {
        fake.error = { if (it == "getCalendar") java.io.IOException("offline") else null }
        val vm = viewModel()
        val collected = mutableListOf<LceState<LidarrCalendarUiState>>()
        backgroundScope.launch(main) { vm.lce.toList(collected) }

        assertTrue(collected.last() is LceState.Error)
    }

    @Test
    fun `deep link selects the day and exposes its albums`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        fake.calendar[2] = album(2, releaseDate = "2026-10-05")
        val vm = viewModel(initialSelectedDate = LocalDate.of(2026, 10, 5))

        assertEquals(LocalDate.of(2026, 10, 5), vm.state.value.selectedDate)
        assertEquals(setOf(1, 2), vm.state.value.selectedDayEntries?.map { it.albumId }?.toSet())
    }

    @Test
    fun `view mode and day selection update state`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        val vm = viewModel()

        vm.setViewMode(LidarrCalendarViewMode.AGENDA)
        vm.selectDate(LocalDate.of(2026, 10, 5))

        assertEquals(LidarrCalendarViewMode.AGENDA, vm.state.value.viewMode)
        assertEquals(LocalDate.of(2026, 10, 5), vm.state.value.selectedDate)
        assertEquals(listOf(1), vm.state.value.selectedDayEntries?.map { it.albumId })
    }

    @Test
    fun `agenda anchor follows the selected month when today is elsewhere`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-12-24")
        val vm = viewModel()
        vm.nextMonth()
        vm.nextMonth()

        assertEquals(YearMonth.of(2026, 12), vm.state.value.month)
        // The agenda lists the December release, anchored at the month's start
        // rather than at October's today.
        assertEquals(LocalDate.of(2026, 12, 24), vm.state.value.agendaDays.single().date)
    }

    private fun viewModel(
        events: Flow<LidarrCalendarRefreshSignal> = emptyFlow(),
        initialSelectedDate: LocalDate? = null,
    ) = LidarrCalendarViewModel(
        repository = repository,
        events = events,
        today = { today },
        initialSelectedDate = initialSelectedDate,
        firstDay = DayOfWeek.SUNDAY,
    )

    private fun album(id: Int, releaseDate: String, monitored: Boolean = true) = AlbumResource(
        id = id,
        title = "Album $id",
        artistId = id,
        monitored = monitored,
        releaseDate = releaseDate,
    )
}
