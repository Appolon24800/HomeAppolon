package dev.appolon.homelab.arr.feature.radarr.activity.data

import dev.appolon.homelab.arr.api.radarr.HistoryApi
import dev.appolon.homelab.arr.api.radarr.model.HistoryResource
import dev.appolon.homelab.arr.api.radarr.model.MovieHistoryEventType
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.net.arrError
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import kotlin.coroutines.CoroutineContext

/**
 * Activity history repository. Reads are cache-first through the
 * [EntityStore]: observe plus a fetchedAt TTL driving refresh; the feed is
 * the paged History endpoint sorted date-descending, recentered on page one
 * by [refresh] and extended page by page by [loadNextPage]. Filters (event
 * type, movie, date window) are applied in the presenters, so the cache stays
 * filter-agnostic and every chip switch is instant and works offline; the
 * spec leaves the integer `eventType` query mapping undefined, which the
 * client-side filter also sidesteps. The only write, mark-as-failed, goes to
 * the server first and falls back to an optimistic cache flip plus an
 * [ActionQueue] enqueue on retryable failure. Everything runs on IO; no
 * function throws.
 */
class RadarrHistoryRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val historyApi: HistoryApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    /** Injectable so unit tests run the whole pipeline on virtual time. */
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    fun observeEvents(): Flow<List<HistoryResource>> =
        store.observe(serverId, HISTORY_EVENT_TYPE, HistoryResource.serializer())

    suspend fun cachedEvents(): List<HistoryResource> =
        store.getAll(serverId, HISTORY_EVENT_TYPE, HistoryResource.serializer())

    /** True when the history cache is absent or older than [ttlMillis]. */
    suspend fun isStale(ttlMillis: Long = HISTORY_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, HISTORY_EVENT_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /**
     * Re-fetches page one and replaces the cache with it, mirroring the web
     * UI recentering on the newest events.
     */
    suspend fun refresh(): ArrResult<ActivityListMeta> = withContext(ioContext) {
        when (val page = fetchPage(PAGE_ONE)) {
            is ArrResult.Err -> page
            is ArrResult.Ok -> {
                val records = page.value.records.orEmpty()
                store.deleteAll(serverId, HISTORY_EVENT_TYPE)
                store.putAll(
                    serverId,
                    HISTORY_EVENT_TYPE,
                    records.map { it.id.toString() to it },
                    HistoryResource.serializer(),
                    fetchedAtMillis = now(),
                )
                val meta = ActivityListMeta(totalRecords = page.value.totalRecords, pagesLoaded = PAGE_ONE)
                putMeta(meta)
                ArrResult.ok(meta)
            }
        }
    }

    /** Refresh only when the TTL expired; the steady-state path after the first load. */
    suspend fun refreshIfStale(ttlMillis: Long = HISTORY_TTL_MILLIS): ArrResult<ActivityListMeta> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(metaOf())

    /**
     * Appends the next unfetched server page to the cache. The fetchedAt
     * stamp is carried over so a deep scroll does not extend the refresh TTL.
     */
    suspend fun loadNextPage(): ArrResult<ActivityPageLoad> = withContext(ioContext) {
        val meta = metaOf()
        val pageRequest = meta.nextPage()
        if (pageRequest == null || meta.endReached(cachedEvents().size)) {
            return@withContext ArrResult.ok(ActivityPageLoad(0, meta, endReached = true))
        }
        when (val page = fetchPage(pageRequest)) {
            is ArrResult.Err -> page
            is ArrResult.Ok -> {
                val records = page.value.records.orEmpty()
                val stamp = store.lastFetchedAt(serverId, HISTORY_EVENT_TYPE) ?: now()
                store.putAll(
                    serverId,
                    HISTORY_EVENT_TYPE,
                    records.map { it.id.toString() to it },
                    HistoryResource.serializer(),
                    fetchedAtMillis = stamp,
                )
                val nextMeta = ActivityListMeta(
                    totalRecords = page.value.totalRecords,
                    pagesLoaded = pageRequest,
                )
                putMeta(nextMeta)
                ArrResult.ok(
                    ActivityPageLoad(
                        appended = records.size,
                        meta = nextMeta,
                        endReached = nextMeta.endReached(cachedEvents().size),
                    ),
                )
            }
        }
    }

    /**
     * Marks a grabbed event as failed (`POST history/failed/{id}`). On a
     * retryable failure the cache row flips optimistically and the POST is
     * enqueued for replay.
     */
    suspend fun markFailed(eventId: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        try {
            historyApi.markHistoryFailed(eventId)
        } catch (e: HttpException) {
            return@withContext enqueueMarkFailed(eventId, e.toArrError())
        } catch (e: IOException) {
            return@withContext enqueueMarkFailed(eventId, ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext enqueueMarkFailed(eventId, e.arrError())
        }
        applyFailed(eventId)
        ArrResult.ok(WriteOutcome.Synced)
    }

    /** Event ids whose mark-failed has not replayed yet, for row badges. */
    fun queuedMarkFailedIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter {
                it.serverId == serverId && it.method == POST_METHOD &&
                    it.path.startsWith(MARK_FAILED_PATH_PREFIX)
            }
            .mapNotNull { it.path.removePrefix(MARK_FAILED_PATH_PREFIX).toIntOrNull() }
            .toSet()
    }

    /** Queued action count for this server, for the pending banner. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    /** Snapshot of the paging cursor, defaults when never refreshed. */
    suspend fun metaOf(): ActivityListMeta =
        store.get(serverId, META_TYPE, META_ID, ActivityListMeta.serializer()) ?: ActivityListMeta()

    private suspend fun putMeta(meta: ActivityListMeta) {
        store.put(serverId, META_TYPE, META_ID, meta, ActivityListMeta.serializer())
    }

    private suspend fun enqueueMarkFailed(eventId: Int, error: ArrError): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        applyFailed(eventId)
        val action = action(
            method = POST_METHOD,
            path = "$MARK_FAILED_PATH_PREFIX$eventId",
            bodyJson = null,
            description = "Mark history event $eventId failed",
        )
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    /** Flips the cached row to downloadFailed so the list reflects the write immediately. */
    private suspend fun applyFailed(eventId: Int) {
        val cached = store.get(serverId, HISTORY_EVENT_TYPE, eventId.toString(), HistoryResource.serializer())
        if (cached != null && cached.eventType != MovieHistoryEventType.DOWNLOAD_FAILED) {
            store.put(
                serverId,
                HISTORY_EVENT_TYPE,
                eventId.toString(),
                cached.copy(eventType = MovieHistoryEventType.DOWNLOAD_FAILED),
                HistoryResource.serializer(),
            )
        }
    }

    private suspend fun fetchPage(page: Int) = try {
        ArrResult.ok(
            historyApi.history(
                page = page,
                pageSize = ACTIVITY_PAGE_SIZE,
                sortKey = SORT_KEY,
                sortDirection = SortDirection.DESCENDING,
            ),
        )
    } catch (e: HttpException) {
        ArrResult.err(e.toArrError())
    } catch (e: IOException) {
        ArrResult.err(ArrError.Network(e))
    } catch (e: Throwable) {
        ArrResult.err(e.arrError())
    }

    private fun action(method: String, path: String, bodyJson: String?, description: String) =
        QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.RADARR,
            method = method,
            path = path,
            bodyJson = bodyJson,
            description = description,
            enqueuedAtMillis = now(),
        )

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        internal val HISTORY_EVENT_TYPE = EntityType("radarr.history.event")
        internal val META_TYPE = EntityType("radarr.history.meta")

        /** One minute: new events arrive through live refresh signals anyway. */
        const val HISTORY_TTL_MILLIS: Long = 60 * 1000

        internal const val META_ID = "events"
        internal const val MARK_FAILED_PATH_PREFIX = "api/v3/history/failed/"
        private const val POST_METHOD = "POST"
        private const val PAGE_ONE = 1

        /** Date-descending keeps the feed newest-first and makes the date-window early stop valid. */
        private const val SORT_KEY = "date"
    }
}
