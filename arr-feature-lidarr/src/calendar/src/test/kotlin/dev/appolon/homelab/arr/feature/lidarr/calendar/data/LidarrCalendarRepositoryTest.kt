package dev.appolon.homelab.arr.feature.lidarr.calendar.data

import dev.appolon.homelab.arr.api.lidarr.CalendarApi
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.calendar.FakeCalendarEntityStore
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.http.Query

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LidarrCalendarRepositoryTest {

    private val serverId = ServerId("s1")
    private val io = UnconfinedTestDispatcher()
    private lateinit var fake: FakeLidarrCalendar
    private lateinit var store: FakeCalendarEntityStore
    private var clock: Long = 1_000_000L

    /** Wraps the fake to count calls and capture the wire window. */
    private class RecordingApi(private val delegate: FakeLidarrCalendar) : CalendarApi {
        var calls = 0
        var lastStart: String? = null
        var lastEnd: String? = null
        var lastUnmonitored: Boolean? = null
        var lastIncludeArtist: Boolean? = null

        override suspend fun getCalendar(
            @Query("start") start: String?,
            @Query("end") end: String?,
            @Query("unmonitored") unmonitored: Boolean?,
            @Query("includeArtist") includeArtist: Boolean?,
            @Query("tags") tags: String?,
        ): List<AlbumResource> {
            calls++
            lastStart = start
            lastEnd = end
            lastUnmonitored = unmonitored
            lastIncludeArtist = includeArtist
            return delegate.getCalendar(start, end, unmonitored, includeArtist, tags)
        }

        override suspend fun getCalendarById(id: Int): AlbumResource =
            delegate.getCalendarById(id)
    }

    private lateinit var api: RecordingApi
    private lateinit var repository: LidarrCalendarRepository

    @Before
    fun setUp() {
        fake = FakeLidarrCalendar()
        api = RecordingApi(fake)
        store = FakeCalendarEntityStore()
        repository = LidarrCalendarRepository(
            serverId = serverId,
            store = store,
            calendarApi = api,
            now = { clock },
            io = io,
        )
    }

    @Test
    fun `refreshMonth fetches the padded window with bare dates and caches mapped entries`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        fake.calendar[2] = album(2, releaseDate = "2026-09-26")
        fake.calendar[3] = album(3, releaseDate = "2026-12-15")

        val result = repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertTrue(result is ArrResult.Ok)
        val window = LidarrCalendarRepository.monthWindow(YearMonth.of(2026, 10))
        assertEquals(window.start.toString(), api.lastStart)
        assertEquals(window.end.toString(), api.lastEnd)
        assertEquals(false, api.lastUnmonitored)
        assertEquals(true, api.lastIncludeArtist)
        val cached = repository.cachedEntries()
        // Mapping trims any release date outside the window no matter how the
        // server matched the album, so prefetches stay self-contained.
        assertEquals(setOf(1, 2), cached.map { it.albumId }.toSet())
        assertEquals("2026-09-26", cached.first { it.albumId == 2 }.releaseDate)
        val meta = store.get(
            serverId,
            LidarrCalendarRepository.WINDOW_TYPE,
            window.id(unmonitored = false),
            LidarrCalendarWindow.serializer(),
        )
        assertEquals(clock, meta?.fetchedAtMillis)
        assertEquals(2, meta?.entryCount)
    }

    @Test
    fun `refreshMonth reconciles albums the server no longer returns`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        fake.calendar[2] = album(2, releaseDate = "2026-10-12")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(setOf(1, 2), repository.cachedEntries().map { it.albumId }.toSet())

        fake.calendar.remove(2)
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertEquals(setOf(1), repository.cachedEntries().map { it.albumId }.toSet())
    }

    @Test
    fun `refreshMonth keeps entries of untouched neighbor windows`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        fake.calendar[2] = album(2, releaseDate = "2026-12-05")
        repository.refreshMonth(YearMonth.of(2026, 12), unmonitored = false)
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        val cached = repository.cachedEntries().map { it.albumId }.toSet()

        assertEquals(setOf(1, 2), cached)
    }

    @Test
    fun `ensureMonth is cache-first and refetches once the TTL expires`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)
        repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(1, api.calls)

        clock += LidarrCalendarRepository.WINDOW_TTL_MILLIS + 1
        repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertEquals(2, api.calls)
    }

    @Test
    fun `unmonitored windows key separately from monitored ones`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05", monitored = false)
        repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertTrue(repository.cachedEntries().isEmpty())

        repository.ensureMonth(YearMonth.of(2026, 10), unmonitored = true)

        assertEquals(listOf(1), repository.cachedEntries().map { it.albumId })
        // Both window rows exist independently: neither fetch invalidates the other.
        assertTrue(repository.isWindowFresh(YearMonth.of(2026, 10), unmonitored = false))
        assertTrue(repository.isWindowFresh(YearMonth.of(2026, 10), unmonitored = true))
    }

    @Test
    fun `purgeEntries clears entries and windows so the toggle refetches clean`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)
        assertTrue(repository.cachedEntries().isNotEmpty())

        repository.purgeEntries()

        assertTrue(repository.cachedEntries().isEmpty())
        assertTrue(
            store.getAll(serverId, LidarrCalendarRepository.WINDOW_TYPE, LidarrCalendarWindow.serializer())
                .isEmpty(),
        )
    }

    @Test
    fun `refreshMonth maps HttpException auth failures to ArrError-Auth`() = runTest {
        fake.error = { HttpExceptionFactory.unauthorized() }

        val result = repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertTrue(result is ArrResult.Err)
        val error = (result as ArrResult.Err).error
        assertTrue(error.toString(), error is ArrError.Auth)
    }

    @Test
    fun `refreshMonth maps transport failures to ArrError without throwing`() = runTest {
        fake.error = { IOException("server unreachable") }

        val result = repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        assertTrue(result is ArrResult.Err)
    }

    @Test
    fun `upcomingCount reads the cache with the horizon`() = runTest {
        val today = LocalDate.of(2026, 10, 5)
        fake.calendar[1] = album(1, releaseDate = "2026-10-06", monitored = true)
        fake.calendar[2] = album(2, releaseDate = "2026-10-06", monitored = true)
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = true)

        assertEquals(2, repository.upcomingCount(today))
    }

    @Test
    fun `observeEntries re-emits after a window write`() = runTest {
        fake.calendar[1] = album(1, releaseDate = "2026-10-05")
        repository.refreshMonth(YearMonth.of(2026, 10), unmonitored = false)

        val entries = repository.observeEntries().first()

        assertEquals(listOf(1), entries.map { it.albumId })
    }

    private fun album(id: Int, releaseDate: String, monitored: Boolean = true) = AlbumResource(
        id = id,
        title = "Album $id",
        artistId = id,
        monitored = monitored,
        releaseDate = releaseDate,
    )
}

/** Builds the 401 the auth-path test needs without leaking response bodies. */
private object HttpExceptionFactory {
    fun unauthorized(): retrofit2.HttpException = retrofit2.HttpException(
        retrofit2.Response.error<Unit>(
            401,
            "{}".toResponseBody("application/json".toMediaType()),
        )
    )
}
