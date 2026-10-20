package dev.appolon.homelab.arr.feature.prowlarr.history.data

import dev.appolon.homelab.arr.api.prowlarr.model.CommandResource
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResource
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.SortDirection
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.WriteOutcome
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.HttpException

/**
 * History repository. The paged listing is cache-first through the
 * [EntityStore]: page one replaces the cached window (server-side clears and
 * filters shrink it) and later pages upsert. The indexer list for filter
 * chips shares the committed indexer entity type, so the indexers screen and
 * this one warm the same cache. Clearing history is a server command; when
 * the call fails retryably it clears the cache optimistically and queues the
 * command for replay.
 */
class ProwlarrHistoryRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val historyApi: suspend (
        page: Int,
        pageSize: Int,
        sortDirection: SortDirection,
        indexerIds: List<Int>?,
    ) -> HistoryResourcePagingResource,
    private val indexerListApi: suspend () -> List<IndexerResource>,
    private val commandApi: suspend (CommandResource) -> CommandResource,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Cached history rows, newest event order applied by the presentation. */
    fun observeHistory(): Flow<List<HistoryResource>> =
        store.observe(serverId, HISTORY_TYPE, HistoryResource.serializer())

    suspend fun cachedHistory(): List<HistoryResource> =
        store.getAll(serverId, HISTORY_TYPE, HistoryResource.serializer())

    /** Cached indexers backing the filter chips; shared with the indexers screen. */
    fun observeIndexers(): Flow<List<IndexerResource>> =
        store.observe(
            serverId,
            ProwlarrIndexersRepository.INDEXER_TYPE,
            IndexerResource.serializer(),
        )

    /** Indexer list with TTL; a fetch failure falls back to stale rows. */
    suspend fun indexers(
        ttlMillis: Long = ProwlarrIndexersRepository.INDEXER_TTL_MILLIS,
    ): ArrResult<List<IndexerResource>> = withContext(io) {
        val cached = store.getAll(
            serverId,
            ProwlarrIndexersRepository.INDEXER_TYPE,
            IndexerResource.serializer(),
        )
        val stamp = store.lastFetchedAt(serverId, ProwlarrIndexersRepository.INDEXER_TYPE)
        if (stamp != null && now() - stamp <= ttlMillis && cached.isNotEmpty()) {
            return@withContext ArrResult.ok(cached)
        }
        val fetched = try {
            indexerListApi()
        } catch (e: HttpException) {
            return@withContext staleOrErr(cached, e.toArrError())
        } catch (e: Throwable) {
            return@withContext staleOrErr(cached, e.arrError())
        }
        reconcileIndexers(fetched)
        ArrResult.ok(fetched)
    }

    /**
     * Loads one page of the query log for [indexerIds] (null means all).
     * Page one replaces the cache; later pages upsert by event id.
     */
    suspend fun loadPage(
        page: Int,
        indexerIds: List<Int>? = null,
    ): ArrResult<HistoryResourcePagingResource> = withContext(io) {
        try {
            val result = historyApi(page, PAGE_SIZE, SortDirection.DESCENDING, indexerIds)
            if (page <= 1) {
                store.deleteAll(serverId, HISTORY_TYPE)
            }
            val records = result.records.orEmpty()
            store.putAll(
                serverId, HISTORY_TYPE,
                records.map { it.id.toString() to it },
                HistoryResource.serializer(),
                fetchedAtMillis = now(),
            )
            ArrResult.ok(result)
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    /**
     * Clears server history through the ClearHistory command. Retryable
     * failures queue the command for replay and drop the cache now so the
     * screen reflects the clear immediately.
     */
    suspend fun clearHistory(): ArrResult<WriteOutcome> = withContext(io) {
        try {
            commandApi(CommandResource(name = CLEAR_HISTORY_COMMAND))
            store.deleteAll(serverId, HISTORY_TYPE)
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            offlineOrErr(e.toArrError()) { queueClear() }
        } catch (e: Throwable) {
            offlineOrErr(e.arrError()) { queueClear() }
        }
    }

    /** True while a ClearHistory command sits in the replay queue. */
    fun clearQueued(): Flow<Boolean> = actionQueue.observe().map { pending ->
        pending.any { action ->
            action.serverId == serverId &&
                action.method == "POST" &&
                action.path == COMMAND_PATH &&
                action.bodyJson?.contains(CLEAR_HISTORY_COMMAND) == true
        }
    }

    private suspend fun queueClear(): ActionId {
        store.deleteAll(serverId, HISTORY_TYPE)
        val action = QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.PROWLARR,
            method = "POST",
            path = COMMAND_PATH,
            bodyJson = ArrJson.encodeToString(
                JsonObject.serializer(),
                buildJsonObject { put("name", CLEAR_HISTORY_COMMAND) },
            ),
            description = "Clear history",
            enqueuedAtMillis = now(),
        )
        actionQueue.enqueue(action)
        return action.actionId
    }

    private suspend fun reconcileIndexers(fetched: List<IndexerResource>) {
        val incoming = fetched.map { it.id.toString() }.toSet()
        val existing = store.getAll(
            serverId,
            ProwlarrIndexersRepository.INDEXER_TYPE,
            IndexerResource.serializer(),
        )
        existing.map { it.id.toString() }
            .filter { it !in incoming }
            .forEach { id ->
                store.delete(serverId, ProwlarrIndexersRepository.INDEXER_TYPE, id)
            }
        store.putAll(
            serverId, ProwlarrIndexersRepository.INDEXER_TYPE,
            fetched.map { it.id.toString() to it },
            IndexerResource.serializer(),
            fetchedAtMillis = now(),
        )
    }

    private fun <T> staleOrErr(cached: List<T>, error: ArrError): ArrResult<List<T>> =
        if (cached.isNotEmpty()) ArrResult.ok(cached) else ArrResult.err(error)

    private inline fun offlineOrErr(
        error: ArrError,
        enqueueOptimistic: () -> ActionId,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val actionId = enqueueOptimistic()
        return ArrResult.ok(WriteOutcome.Queued(actionId))
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        internal val HISTORY_TYPE = EntityType("prowlarr.history")

        /** Server command name that purges the history table. */
        const val CLEAR_HISTORY_COMMAND = "ClearHistory"

        internal const val COMMAND_PATH = "api/v1/command"

        /** One page of the query log; the list loads more on scroll. */
        const val PAGE_SIZE: Int = 50
    }
}
