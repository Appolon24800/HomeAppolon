package dev.appolon.homelab.arr.feature.radarr.calendar.data

import dev.appolon.homelab.arr.api.radarr.CalendarApi
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.core.ArrTime
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Calendar repository. Windowed, cache-first reads through the [EntityStore]:
 * every fetch covers one month padded by [WINDOW_PAD_DAYS] on both ends, so
 * the month grid's leading and trailing cells and the adjacent-month
 * prefetches all resolve from the cache. Freshness is tracked per window with
 * [RadarrCalendarWindow] rows, letting navigation through already-prefetched
 * months skip the network entirely.
 *
 * The calendar is read-only on this screen, so there is no action queue path;
 * refresh failures surface as LCE errors while cached days stay visible.
 */
class RadarrCalendarRepository(
    val serverId: ServerId,
    private val store: EntityStore,
    private val calendarApi: CalendarApi,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        val ENTRY_TYPE = EntityType("radarr.calendar-entry")
        val WINDOW_TYPE = EntityType("radarr.calendar-window")

        /** Windows stay fresh for ten minutes, matching the sync poll floor. */
        const val WINDOW_TTL_MILLIS: Long = 10 * 60 * 1000

        /** Padding around a month so the grid's overflow cells have data. */
        const val WINDOW_PAD_DAYS: Long = 7

        fun monthWindow(month: YearMonth): CalendarWindow = CalendarWindow(
            start = month.atDay(1).minusDays(WINDOW_PAD_DAYS),
            end = month.atEndOfMonth().plusDays(WINDOW_PAD_DAYS),
        )

        /** Wire form the API expects: full ISO offset date-time at UTC bounds of the day. */
        fun wireStart(date: LocalDate): String =
            ArrTime.formatWire(date.atStartOfDay(ZoneOffset.UTC).toInstant())

        fun wireEnd(date: LocalDate): String =
            ArrTime.formatWire(date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(1))
    }

    fun observeEntries(): Flow<List<RadarrCalendarEntry>> =
        store.observe(serverId, ENTRY_TYPE, RadarrCalendarEntry.serializer())

    suspend fun cachedEntries(): List<RadarrCalendarEntry> =
        store.getAll(serverId, ENTRY_TYPE, RadarrCalendarEntry.serializer())

    /** True when a window row exists and is younger than [ttlMillis]. */
    suspend fun isWindowFresh(
        month: YearMonth,
        unmonitored: Boolean,
        ttlMillis: Long = WINDOW_TTL_MILLIS,
    ): Boolean {
        val window = monthWindow(month)
        val row = store.get(serverId, WINDOW_TYPE, window.id(unmonitored), RadarrCalendarWindow.serializer())
        return row != null && now() - row.fetchedAtMillis <= ttlMillis
    }

    /** Cache-first: fetches only when the window is missing or stale. */
    suspend fun ensureMonth(
        month: YearMonth,
        unmonitored: Boolean,
        ttlMillis: Long = WINDOW_TTL_MILLIS,
    ): ArrResult<Unit> {
        if (isWindowFresh(month, unmonitored, ttlMillis)) return ArrResult.ok(Unit)
        return refreshMonth(month, unmonitored)
    }

    /**
     * Fetches one month window and reconciles the cache row by row: incoming
     * events are written through, cached events inside the window that the
     * server no longer returns are deleted. Events outside the window are
     * never touched, so prefetching one month cannot clobber another.
     */
    suspend fun refreshMonth(month: YearMonth, unmonitored: Boolean): ArrResult<Unit> = withContext(io) {
        val window = monthWindow(month)
        val fetched = try {
            calendarApi.calendar(
                start = wireStart(window.start),
                end = wireEnd(window.end),
                unmonitored = unmonitored,
            )
        } catch (e: HttpException) {
            return@withContext ArrResult.err(
                if (e.code() == 401 || e.code() == 403) ArrError.Auth(e.code()) else ArrError.Http(e.code(), e.message()),
            )
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val incoming = fetched.flatMap(::calendarEntriesOf)
            .filter { window.contains(LocalDate.parse(it.eventDate)) }
        val incomingIds = incoming.map { RadarrCalendarEntry.entityId(it.movieId, it.eventKind) }.toSet()
        cachedEntries()
            .filter { entry -> window.contains(LocalDate.parse(entry.eventDate)) }
            .forEach { entry ->
                val id = RadarrCalendarEntry.entityId(entry.movieId, entry.eventKind)
                if (id !in incomingIds) {
                    store.delete(serverId, ENTRY_TYPE, id)
                }
            }
        store.putAll(
            serverId,
            ENTRY_TYPE,
            incoming.map { RadarrCalendarEntry.entityId(it.movieId, it.eventKind) to it },
            RadarrCalendarEntry.serializer(),
            fetchedAtMillis = now(),
        )
        store.put(
            serverId,
            WINDOW_TYPE,
            window.id(unmonitored),
            RadarrCalendarWindow(
                start = window.start.toString(),
                end = window.end.toString(),
                unmonitored = unmonitored,
                fetchedAtMillis = now(),
                entryCount = incoming.size,
            ),
            RadarrCalendarWindow.serializer(),
        )
        ArrResult.ok(Unit)
    }

    /**
     * Drops every cached entry and window row. The unmonitored toggle changes
     * the server-side filter, so a mixed cache would show stale rows; purging
     * and refetching the visible windows keeps the two views consistent.
     */
    suspend fun purgeEntries() {
        store.deleteAll(serverId, ENTRY_TYPE)
        store.deleteAll(serverId, WINDOW_TYPE)
    }

    /** Distinct monitored movies with an event in `[today, today + horizonDays]`. */
    suspend fun upcomingCount(today: LocalDate, horizonDays: Int = UPCOMING_HORIZON_DAYS): Int =
        countUpcoming(cachedEntries().filter { it.monitored }, today, horizonDays)
}

private const val UPCOMING_HORIZON_DAYS = 7
