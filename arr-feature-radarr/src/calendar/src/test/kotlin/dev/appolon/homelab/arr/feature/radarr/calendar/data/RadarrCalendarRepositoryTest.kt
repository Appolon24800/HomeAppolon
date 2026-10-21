package dev.appolon.homelab.arr.feature.radarr.calendar.data

import dev.appolon.homelab.arr.api.radarr.CalendarApi
import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.calendar.FakeCalendarEntityStore
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.http.Query

class RadarrCalendarRepositoryTest {

    private val serverId = ServerId("s1")
    private val io = UnconfinedTestDispatcher()
    private lateinit var fake: FakeRadarrCalendar
    private lateinit var store: FakeCalendarEntityStore
    private var clock: Long = 1_000_000L

    /** Wraps the fake to count calls and capture the wire window. */
    private class RecordingApi(private val delegate: FakeRadarrCalendar) : CalendarApi {
        var calls = 0
        var lastStart: String? = null
        var lastEnd: String? = null
        var lastUnmonitored: Boolean? = null

        override suspend fun calendar(
            @Query("start") start: String?,
            @Query("end") end: String?,
            @Query("unmonitored") unmonitored: Boolean?,
            @Query("tags") tags: String?,
        ): List<MovieResource> {
            calls++
            lastStart = start
            lastEnd = end
            lastUnmonitored = unmonitored
            return delegate.calendar(start, end, unmonitored, tags)
        }
    }

    private lateinit var api: RecordingApi
    private lateinit var repository: RadarrCalendarRepository

    @Before
    fun setUp() {
        fake = FakeRadarrCalendar()
        api = RecordingApi(fake)
        store = FakeCalendarEntityStore()
        repository = RadarrCalendarRepository(
            serverId = serverId,
            store = store,
            calendarApi = api,
            now = { clock },
            io = io,
        )
    }

    @Test
    fun `refreshMonth fetches the padded window and caches mapped entries`() = runTest {
        fake.movies += octoberMovie(1, "Alpha", "2026-10-05T00:00:00Z")
        fake.movies += octoberMovie(2, "Edge", "2026-09-26T00:00:00Z")
        fake.movies += octoberMovie(3, "Outside", "2026-12-15T00:00:00Z")

        val result = repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertTrue(result is ArrResult.Ok)
        val window = RadarrCalendarRepository.monthWindow(YearMonth.of(2026, 10))
        assertEquals(RadarrCalendarRepository.wireStart(window.start), api.lastStart)
        assertEquals(RadarrCalendarRepository.wireEnd(window.end), api.lastEnd)
        assertEquals(false, api.lastUnmonitored)
        val cached = repository.cachedEntries()
        // Mapping trims any event date outside the window no matter how the
        // server matched the movie, so prefetches stay self-contained.
        assertEquals(setOf(1, 2), cached.map { it.movieId }.toSet())
        assertEquals(
            LocalDate.of(2026, 9, 26).toString(),
            cached.first { it.movieId == 2 }.eventDate,
        )
        val meta = store.get(
            serverId,
            RadarrCalendarRepository.WINDOW_TYPE,
            window.id(unmonitored = false),
            RadarrCalendarWindow.serializer(),
        )
        assertEquals(clock, meta?.fetchedAtMillis)
    }

    @Test
    fun `refreshMonth reconciles movies the server no longer returns`() = runTest {
        fake.movies += octoberMovie(1, "Alpha", "2026-10-05T00:00:00Z")
        fake.movies += octoberMovie(2, "Beta", "2026-10-12T00:00:00Z")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(setOf(1, 2), repository.cachedEntries().map { it.movieId }.toSet())

        fake.movies.removeAll { it.id == 2 }
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertEquals(setOf(1), repository.cachedEntries().map { it.movieId }.toSet())
    }

    @Test
    fun `ensureMonth is cache-first and refetches once the TTL expires`() = runTest {
        fake.movies += octoberMovie(1, "Alpha", "2026-10-05T00:00:00Z")
        repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)
        repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(1, api.calls)

        clock += RadarrCalendarRepository.WINDOW_TTL_MILLIS + 1
        repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(2, api.calls)
    }

    @Test
    fun `windows of different months cache independently`() = runTest {
        fake.movies += octoberMovie(1, "Alpha", "2026-10-05T00:00:00Z")
        fake.movies += octoberMovie(2, "November", "2026-11-20T00:00:00Z")

        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(setOf(1), repository.cachedEntries().map { it.movieId }.toSet())

        repository.ensureMonth(YearMonth.of(2026, 11), unmonitored = false)
        assertEquals(setOf(1, 2), repository.cachedEntries().map { it.movieId }.toSet())
    }

    @Test
    fun `network failures surface as errors and leave the cache intact`() = runTest {
        fake.movies += octoberMovie(1, "Alpha", "2026-10-05T00:00:00Z")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        fake.errorFor = { if (it == "calendar") IOException("offline") else null }

        clock += RadarrCalendarRepository.WINDOW_TTL_MILLIS + 1
        val result = repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertTrue(result is ArrResult.Err)
        assertEquals(setOf(1), repository.cachedEntries().map { it.movieId }.toSet())
    }

    @Test
    fun `purge clears entries and window rows`() = runTest {
        fake.movies += octoberMovie(1, "Alpha", "2026-10-05T00:00:00Z")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertTrue(repository.cachedEntries().isNotEmpty())

        repository.purgeEntries()

        assertTrue(repository.cachedEntries().isEmpty())
        assertTrue(
            store.getAll(serverId, RadarrCalendarRepository.WINDOW_TYPE, RadarrCalendarWindow.serializer())
                .isEmpty(),
        )
    }

    @Test
    fun `upcomingCount counts distinct monitored movies within the horizon`() = runTest {
        fake.movies += movie(
            id = 1,
            monitored = true,
            physicalRelease = "2026-10-24T00:00:00Z",
            inCinemas = "2026-10-21T00:00:00Z",
            digitalRelease = "2026-10-24T00:00:00Z",
        )
        fake.movies += movie(id = 2, monitored = true, physicalRelease = "2026-10-27T00:00:00Z")
        fake.movies += movie(id = 3, monitored = true, physicalRelease = "2026-11-15T00:00:00Z")
        fake.movies += movie(id = 4, monitored = false, physicalRelease = "2026-10-22T00:00:00Z")
        fake.movies += movie(id = 5, monitored = true, physicalRelease = "2026-10-19T00:00:00Z")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = true)

        assertEquals(2, repository.upcomingCount(LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun `observeEntries re-emits as windows refresh`() = runTest {
        fake.movies += octoberMovie(1, "Alpha", "2026-10-05T00:00:00Z")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(1, repository.observeEntries().first().size)

        fake.movies += octoberMovie(2, "Beta", "2026-10-12T00:00:00Z")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertEquals(2, repository.observeEntries().first().size)
    }

    private fun octoberMovie(id: Int, title: String, date: String): MovieResource = MovieResource(
        id = id,
        title = title,
        monitored = true,
        physicalRelease = date,
    )

    private fun movie(
        id: Int,
        monitored: Boolean,
        physicalRelease: String,
        inCinemas: String? = null,
        digitalRelease: String? = null,
    ): MovieResource = MovieResource(
        id = id,
        title = "Movie $id",
        monitored = monitored,
        inCinemas = inCinemas,
        physicalRelease = physicalRelease,
        digitalRelease = digitalRelease,
    )
}
