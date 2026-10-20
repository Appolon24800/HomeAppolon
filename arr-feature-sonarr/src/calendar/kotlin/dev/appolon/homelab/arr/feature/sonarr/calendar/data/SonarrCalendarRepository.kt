package dev.appolon.homelab.arr.feature.sonarr.calendar.data

import dev.appolon.homelab.arr.api.sonarr.CalendarApi
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.net.arrError
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import retrofit2.HttpException

/**
 * Bookkeeping row for one fetched window so month prefetches can check
 * freshness per window instead of per entity type.
 */
@Serializable
data class SonarrCalendarWindow(
    val start: String,
    val end: String,
    val unmonitored: Boolean,
    val fetchedAtMillis: Long,
    val entryCount: Int = 0,
)

/** Inclusive date window the calendar fetches for one month plus its grid padding. */
data class CalendarWindow(val start: LocalDate, val end: LocalDate) {

    operator fun contains(date: LocalDate): Boolean = date >= start && date <= end

    /** Window row id under [SonarrCalendarRepository.WINDOW_TYPE]. */
    fun id(unmonitored: Boolean): String = "$start:$end:$unmonitored"

    companion object {
        fun of(id: String): CalendarWindow? = runCatching {
            val parts = id.split(':')
            CalendarWindow(LocalDate.parse(parts[0]), LocalDate.parse(parts[1]))
        }.getOrNull()
    }
}

/**
 * Episode calendar repository. Windowed, cache-first reads through the
 * [EntityStore]: every fetch covers one month padded by
 * [SonarrCalendarRepository.WINDOW_PAD_DAYS] on both ends, so the month
 * grid's leading and trailing cells and the adjacent-month prefetches all
 * resolve from the cache. Freshness is tracked per window with
 * [SonarrCalendarWindow] rows, letting navigation through already-prefetched
 * months skip the network entirely.
 *
 * The calendar is read-only on this screen, so there is no action queue path;
 * refresh failures surface as LCE errors while cached days stay visible.
 */
class SonarrCalendarRepository(
    val serverId: ServerId,
    private val store: EntityStore,
    private val calendarApi: CalendarApi,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        val ENTRY_TYPE = EntityType("sonarr.calendar-entry")
        val WINDOW_TYPE = EntityType("sonarr.calendar-window")

        /** Windows stay fresh for ten minutes, matching the sync poll floor. */
        const val WINDOW_TTL_MILLIS: Long = 10 * 60 * 1000

        /** Padding around a month so the grid's overflow cells have data. */
        const val WINDOW_PAD_DAYS: Long = 7

        fun monthWindow(month: YearMonth): CalendarWindow = CalendarWindow(
            start = month.atDay(1).minusDays(WINDOW_PAD_DAYS),
            end = month.atEndOfMonth().plusDays(WINDOW_PAD_DAYS),
        )

        /**
         * Wire form the API expects: bare ISO dates. Sonarr filters the
         * calendar on episode air days, and a bare date keeps both window
         * bounds inclusive of that day, unlike an offset date-time whose
         * string form shifts the comparison. Padding absorbs the skew between
         * `airDate` and `airDateUtc` at the window edges.
         */
        fun wireStart(date: LocalDate): String = date.toString()

        fun wireEnd(date: LocalDate): String = date.toString()
    }

    fun observeEntries(): Flow<List<SonarrCalendarEntry>> =
        store.observe(serverId, ENTRY_TYPE, SonarrCalendarEntry.serializer())

    suspend fun cachedEntries(): List<SonarrCalendarEntry> =
        store.getAll(serverId, ENTRY_TYPE, SonarrCalendarEntry.serializer())

    /** True when a window row exists and is younger than [ttlMillis]. */
    suspend fun isWindowFresh(
        month: YearMonth,
        unmonitored: Boolean,
        ttlMillis: Long = WINDOW_TTL_MILLIS,
    ): Boolean {
        val window = monthWindow(month)
        val row = store.get(serverId, WINDOW_TYPE, window.id(unmonitored), SonarrCalendarWindow.serializer())
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
     * episodes are written through, cached episodes inside the window that
     * the server no longer returns are deleted. Entries outside the window
     * are never touched, so prefetching one month cannot clobber another.
     */
    suspend fun refreshMonth(month: YearMonth, unmonitored: Boolean): ArrResult<Unit> = withContext(io) {
        val window = monthWindow(month)
        val fetched = try {
            calendarApi.list(
                start = wireStart(window.start),
                end = wireEnd(window.end),
                unmonitored = unmonitored,
                includeSeries = true,
            )
        } catch (e: HttpException) {
            return@withContext ArrResult.err(
                if (e.code() == 401 || e.code() == 403) ArrError.Auth(e.code()) else ArrError.Http(e.code(), e.message()),
            )
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val incoming = fetched.mapNotNull(::calendarEntryOf)
            .filter { window.contains(LocalDate.parse(it.airDate)) }
        val incomingIds = incoming.map { SonarrCalendarEntry.entityId(it.episodeId) }.toSet()
        cachedEntries()
            .filter { entry -> window.contains(LocalDate.parse(entry.airDate)) }
            .forEach { entry ->
                val id = SonarrCalendarEntry.entityId(entry.episodeId)
                if (id !in incomingIds) {
                    store.delete(serverId, ENTRY_TYPE, id)
                }
            }
        store.putAll(
            serverId,
            ENTRY_TYPE,
            incoming.map { SonarrCalendarEntry.entityId(it.episodeId) to it },
            SonarrCalendarEntry.serializer(),
            fetchedAtMillis = now(),
        )
        store.put(
            serverId,
            WINDOW_TYPE,
            window.id(unmonitored),
            SonarrCalendarWindow(
                start = window.start.toString(),
                end = window.end.toString(),
                unmonitored = unmonitored,
                fetchedAtMillis = now(),
                entryCount = incoming.size,
            ),
            SonarrCalendarWindow.serializer(),
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

    /** Distinct series with a monitored episode in `[today, today + horizonDays]`. */
    suspend fun upcomingCount(today: LocalDate, horizonDays: Int = UPCOMING_HORIZON_DAYS): Int =
        countUpcoming(cachedEntries(), today, horizonDays)
}

private const val UPCOMING_HORIZON_DAYS = 7
