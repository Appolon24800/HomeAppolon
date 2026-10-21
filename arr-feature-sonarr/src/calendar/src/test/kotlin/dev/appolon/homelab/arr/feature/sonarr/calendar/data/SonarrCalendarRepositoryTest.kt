package dev.appolon.homelab.arr.feature.sonarr.calendar.data

import dev.appolon.homelab.arr.api.sonarr.CalendarApi
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.calendar.FakeCalendarEntityStore
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

class SonarrCalendarRepositoryTest {

    private val serverId = ServerId("s1")
    private val io = UnconfinedTestDispatcher()
    private lateinit var fake: FakeSonarrCalendar
    private lateinit var store: FakeCalendarEntityStore
    private var clock: Long = 1_000_000L

    /** Wraps the fake to count calls and capture the wire window. */
    private class RecordingApi(private val delegate: FakeSonarrCalendar) : CalendarApi {
        var calls = 0
        var lastStart: String? = null
        var lastEnd: String? = null
        var lastUnmonitored: Boolean? = null
        var lastIncludeSeries: Boolean? = null

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
            lastStart = start
            lastEnd = end
            lastUnmonitored = unmonitored
            lastIncludeSeries = includeSeries
            return delegate.calendar.list(start, end, unmonitored, includeSeries, includeEpisodeFile, includeEpisodeImages, tags)
        }

        override suspend fun get(id: Int) = delegate.calendar.get(id)
    }

    private lateinit var api: RecordingApi
    private lateinit var repository: SonarrCalendarRepository

    @Before
    fun setUp() {
        fake = FakeSonarrCalendar()
        api = RecordingApi(fake)
        store = FakeCalendarEntityStore()
        repository = SonarrCalendarRepository(
            serverId = serverId,
            store = store,
            calendarApi = api,
            now = { clock },
            io = io,
        )
    }

    @Test
    fun `refreshMonth fetches the padded window with bare dates and caches mapped entries`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        fake.calendarStore[2] = episode(2, airDate = "2026-09-26")
        fake.calendarStore[3] = episode(3, airDate = "2026-12-15")

        val result = repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertTrue(result is ArrResult.Ok)
        val window = SonarrCalendarRepository.monthWindow(YearMonth.of(2026, 10))
        assertEquals(window.start.toString(), api.lastStart)
        assertEquals(window.end.toString(), api.lastEnd)
        assertEquals(false, api.lastUnmonitored)
        assertEquals(true, api.lastIncludeSeries)
        val cached = repository.cachedEntries()
        // Mapping trims any air date outside the window no matter how the
        // server matched the episode, so prefetches stay self-contained.
        assertEquals(setOf(1, 2), cached.map { it.episodeId }.toSet())
        assertEquals("2026-09-26", cached.first { it.episodeId == 2 }.airDate)
        val meta = store.get(
            serverId,
            SonarrCalendarRepository.WINDOW_TYPE,
            window.id(unmonitored = false),
            SonarrCalendarWindow.serializer(),
        )
        assertEquals(clock, meta?.fetchedAtMillis)
        assertEquals(2, meta?.entryCount)
    }

    @Test
    fun `refreshMonth reconciles episodes the server no longer returns`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        fake.calendarStore[2] = episode(2, airDate = "2026-10-12")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(setOf(1, 2), repository.cachedEntries().map { it.episodeId }.toSet())

        fake.calendarStore.remove(2)
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertEquals(setOf(1), repository.cachedEntries().map { it.episodeId }.toSet())
    }

    @Test
    fun `ensureMonth is cache-first and refetches once the TTL expires`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)
        repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(1, api.calls)

        clock += SonarrCalendarRepository.WINDOW_TTL_MILLIS + 1
        repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(2, api.calls)
    }

    @Test
    fun `windows of different months cache independently`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        fake.calendarStore[2] = episode(2, airDate = "2026-11-20")

        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(setOf(1), repository.cachedEntries().map { it.episodeId }.toSet())

        repository.ensureMonth(YearMonth.of(2026, 11), unmonitored = false)
        assertEquals(setOf(1, 2), repository.cachedEntries().map { it.episodeId }.toSet())
    }

    @Test
    fun `network failures surface as errors and leave the cache intact`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        fake.error = { if (it == "Calendar.list") IOException("offline") else null }

        clock += SonarrCalendarRepository.WINDOW_TTL_MILLIS + 1
        val result = repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertTrue(result is ArrResult.Err)
        assertEquals(setOf(1), repository.cachedEntries().map { it.episodeId }.toSet())
    }

    @Test
    fun `purge clears entries and window rows`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertTrue(repository.cachedEntries().isNotEmpty())

        repository.purgeEntries()

        assertTrue(repository.cachedEntries().isEmpty())
        assertTrue(
            store.getAll(serverId, SonarrCalendarRepository.WINDOW_TYPE, SonarrCalendarWindow.serializer())
                .isEmpty(),
        )
    }

    @Test
    fun `upcomingCount counts distinct series with monitored episodes in the horizon`() = runTest {
        fake.calendarStore[11] = episode(11, seriesId = 1, airDate = "2026-10-20", monitored = true)
        fake.calendarStore[12] = episode(12, seriesId = 1, airDate = "2026-10-24", monitored = true)
        fake.calendarStore[21] = episode(21, seriesId = 2, airDate = "2026-10-27", monitored = true)
        fake.calendarStore[22] = episode(22, seriesId = 2, airDate = "2026-10-28", monitored = true)
        fake.calendarStore[31] = episode(31, seriesId = 3, airDate = "2026-10-22", monitored = false)
        fake.calendarStore[41] = episode(41, seriesId = 4, airDate = "2026-11-15", monitored = true)
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = true)
        repository.refreshMonth(YearMonth.of(2026, 11), unmonitored = true)

        assertEquals(2, repository.upcomingCount(LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun `observeEntries re-emits as windows refresh`() = runTest {
        fake.calendarStore[1] = episode(1, airDate = "2026-10-05")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(1, repository.observeEntries().first().size)

        fake.calendarStore[2] = episode(2, airDate = "2026-10-12")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertEquals(2, repository.observeEntries().first().size)
    }

    private fun episode(
        id: Int,
        seriesId: Int = 1,
        airDate: String,
        monitored: Boolean = true,
    ): EpisodeResource = EpisodeResource(
        id = id,
        seriesId = seriesId,
        seasonNumber = 1,
        episodeNumber = id,
        title = "Episode $id",
        airDate = airDate,
        monitored = monitored,
    )
}
