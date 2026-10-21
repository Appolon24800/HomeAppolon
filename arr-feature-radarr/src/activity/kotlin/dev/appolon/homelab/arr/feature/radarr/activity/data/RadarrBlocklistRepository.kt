package dev.appolon.homelab.arr.feature.radarr.activity.data

import dev.appolon.homelab.arr.api.radarr.BlocklistApi
import dev.appolon.homelab.arr.api.radarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.radarr.model.BlocklistResource
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
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import kotlin.coroutines.CoroutineContext

/**
 * Blocklist repository, paging like the history repository: cache-first with
 * a fetchedAt TTL, refresh recenters on page one, load-more appends. Both
 * writes (remove one, clear all) go to the server first and fall back to an
 * optimistic cache change plus an [ActionQueue] enqueue on retryable
 * failure, so the list always reflects the user's intent immediately. No
 * function throws; everything runs on IO.
 */
class RadarrBlocklistRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val blocklistApi: BlocklistApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    /** Injectable so unit tests run the whole pipeline on virtual time. */
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    fun observeEntries(): Flow<List<BlocklistResource>> =
        store.observe(serverId, BLOCKLIST_ENTRY_TYPE, BlocklistResource.serializer())

    suspend fun cachedEntries(): List<BlocklistResource> =
        store.getAll(serverId, BLOCKLIST_ENTRY_TYPE, BlocklistResource.serializer())

    /** True when the blocklist cache is absent or older than [ttlMillis]. */
    suspend fun isStale(ttlMillis: Long = BLOCKLIST_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, BLOCKLIST_ENTRY_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /** Re-fetches page one and replaces the cache with it. */
    suspend fun refresh(): ArrResult<ActivityListMeta> = withContext(ioContext) {
        when (val page = fetchPage(PAGE_ONE)) {
            is ArrResult.Err -> page
            is ArrResult.Ok -> {
                val records = page.value.records.orEmpty()
                store.deleteAll(serverId, BLOCKLIST_ENTRY_TYPE)
                store.putAll(
                    serverId,
                    BLOCKLIST_ENTRY_TYPE,
                    records.map { it.id.toString() to it },
                    BlocklistResource.serializer(),
                    fetchedAtMillis = now(),
                )
                val meta = ActivityListMeta(totalRecords = page.value.totalRecords, pagesLoaded = PAGE_ONE)
                putMeta(meta)
                ArrResult.ok(meta)
            }
        }
    }

    /** Refresh only when the TTL expired. */
    suspend fun refreshIfStale(ttlMillis: Long = BLOCKLIST_TTL_MILLIS): ArrResult<ActivityListMeta> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(metaOf())

    /**
     * Appends the next unfetched server page to the cache. The fetchedAt
     * stamp is carried over so a deep scroll does not extend the refresh TTL.
     */
    suspend fun loadNextPage(): ArrResult<ActivityPageLoad> = withContext(ioContext) {
        val meta = metaOf()
        val pageRequest = meta.nextPage()
        if (pageRequest == null || meta.endReached(cachedEntries().size)) {
            return@withContext ArrResult.ok(ActivityPageLoad(0, meta, endReached = true))
        }
        when (val page = fetchPage(pageRequest)) {
            is ArrResult.Err -> page
            is ArrResult.Ok -> {
                val records = page.value.records.orEmpty()
                val stamp = store.lastFetchedAt(serverId, BLOCKLIST_ENTRY_TYPE) ?: now()
                store.putAll(
                    serverId,
                    BLOCKLIST_ENTRY_TYPE,
                    records.map { it.id.toString() to it },
                    BlocklistResource.serializer(),
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
                        endReached = nextMeta.endReached(cachedEntries().size),
                    ),
                )
            }
        }
    }

    /**
     * Removes one entry. When offline, the removal applies optimistically to
     * the cache and the DELETE is enqueued for replay.
     */
    suspend fun removeEntry(entryId: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        val response = try {
            blocklistApi.removeBlocklist(entryId)
        } catch (e: HttpException) {
            return@withContext enqueueRemoval(listOf(entryId), single = true, e.toArrError())
        } catch (e: IOException) {
            return@withContext enqueueRemoval(listOf(entryId), single = true, ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext enqueueRemoval(listOf(entryId), single = true, e.arrError())
        }
        if (response.isSuccessful) {
            store.delete(serverId, BLOCKLIST_ENTRY_TYPE, entryId.toString())
            ArrResult.ok(WriteOutcome.Synced)
        } else {
            ArrResult.err(response.toArrError())
        }
    }

    /**
     * Removes every cached entry in one bulk DELETE. The server's bulk
     * endpoint is id-driven, so "all" means every entry this device has
     * loaded; a pull-to-refresh reconciles anything beyond the loaded pages.
     * When offline, the rows clear optimistically and the bulk DELETE is
     * enqueued for replay.
     */
    suspend fun clearAll(): ArrResult<WriteOutcome> = withContext(ioContext) {
        val ids = cachedEntries().map { it.id }
        if (ids.isEmpty()) {
            return@withContext ArrResult.ok(WriteOutcome.Synced)
        }
        try {
            blocklistApi.removeBlocklistBulk(BlocklistBulkResource(ids))
            applyCleared()
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            enqueueRemoval(ids, single = false, e.toArrError())
        } catch (e: IOException) {
            enqueueRemoval(ids, single = false, ArrError.Network(e))
        } catch (e: Throwable) {
            enqueueRemoval(ids, single = false, e.arrError())
        }
    }

    /** Entry ids with a queued removal, for per-row queued badges. */
    fun queuedRemovedIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.method == DELETE_METHOD }
            .flatMap(::idsOf)
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

    /** Ids targeted by a queued removal action: single deletes and bulk bodies alike. */
    private fun idsOf(action: QueuedAction): Sequence<Int> = when {
        // The bulk path also starts with the entry prefix, so it must be checked first.
        action.path == BULK_PATH -> bodyIds(action.bodyJson)

        action.path.startsWith(ENTRY_PATH_PREFIX) -> sequenceOf(
            action.path.removePrefix(ENTRY_PATH_PREFIX).substringBefore('?').toIntOrNull(),
        ).filterNotNull()

        else -> emptySequence()
    }

    private fun bodyIds(bodyJson: String?): Sequence<Int> {
        if (bodyJson == null) return emptySequence()
        val decoded = runCatching {
            ArrJson.decodeFromString(BlocklistBulkResource.serializer(), bodyJson)
        }.getOrNull() ?: return emptySequence()
        return decoded.ids.orEmpty().asSequence()
    }

    private suspend fun applyCleared() {
        store.deleteAll(serverId, BLOCKLIST_ENTRY_TYPE)
        putMeta(ActivityListMeta(totalRecords = 0, pagesLoaded = 0))
    }

    private suspend fun enqueueRemoval(
        ids: List<Int>,
        single: Boolean,
        error: ArrError,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val action = if (single && ids.size == 1) {
            // Optimistic removal: the row disappears now, the DELETE replays later.
            store.delete(serverId, BLOCKLIST_ENTRY_TYPE, ids.single().toString())
            action(
                method = DELETE_METHOD,
                path = "$ENTRY_PATH_PREFIX${ids.single()}",
                bodyJson = null,
                description = "Remove blocklist entry ${ids.single()}",
            )
        } else {
            applyCleared()
            action(
                method = DELETE_METHOD,
                path = BULK_PATH,
                bodyJson = ArrJson.encodeToString(
                    BlocklistBulkResource.serializer(),
                    BlocklistBulkResource(ids = ids),
                ),
                description = "Clear blocklist (${ids.size} entries)",
            )
        }
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    private suspend fun fetchPage(page: Int) = try {
        ArrResult.ok(
            blocklistApi.blocklist(
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
            actionId = ActionId(UUID.nameUUIDFromBytes(
                "$serverId:$method:$path:$bodyJson".toByteArray(Charsets.UTF_8),
            ).toString()),
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

    private fun <T> Response<T>.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> {
            val snippet = runCatching { errorBody()?.string() }.getOrNull()
                ?.trim()
                ?.take(200)
                ?.takeIf { it.isNotEmpty() }
            ArrError.Http(code(), snippet)
        }
    }

    companion object {
        internal val BLOCKLIST_ENTRY_TYPE = EntityType("radarr.blocklist.entry")
        internal val META_TYPE = EntityType("radarr.blocklist.meta")

        /** One minute; blocklists change only through queue removals. */
        const val BLOCKLIST_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        internal const val META_ID = "entries"
        internal const val ENTRY_PATH_PREFIX = "api/v3/blocklist/"
        internal const val BULK_PATH = "api/v3/blocklist/bulk"
        private const val DELETE_METHOD = "DELETE"
        private const val PAGE_ONE = 1

        /** Date-descending keeps the newest rejections first. */
        private const val SORT_KEY = "date"
    }
}
