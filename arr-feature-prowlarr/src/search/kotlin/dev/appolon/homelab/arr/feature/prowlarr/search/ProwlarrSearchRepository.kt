package dev.appolon.homelab.arr.feature.prowlarr.search

import dev.appolon.homelab.arr.api.prowlarr.DownloadClientApi
import dev.appolon.homelab.arr.api.prowlarr.HistoryApi
import dev.appolon.homelab.arr.api.prowlarr.IndexerApi
import dev.appolon.homelab.arr.api.prowlarr.IndexerDefaultCategoriesApi
import dev.appolon.homelab.arr.api.prowlarr.IndexerStatsApi
import dev.appolon.homelab.arr.api.prowlarr.SearchApi
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.api.prowlarr.model.ReleaseResource
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
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import retrofit2.HttpException

@Serializable
internal data class SearchCacheEntry(val payload: String, val fetchedAt: Long)

/** Cache-first reads and replayable grabs, scoped to one Prowlarr server. */
class ProwlarrSearchRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val queue: ActionQueue,
    private val searchApi: SearchApi,
    private val historyApi: HistoryApi,
    private val statsApi: IndexerStatsApi,
    private val indexerApi: IndexerApi,
    private val categoriesApi: IndexerDefaultCategoriesApi,
    private val clientsApi: DownloadClientApi,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun options(force: Boolean = false): ArrResult<SearchOptions> = load(
        "options", SearchOptions.serializer(), force,
    ) {
        SearchOptions(
            indexerApi.getIndexers(), categoriesApi.getCategories(),
            clientsApi.getDownloadClients(),
        )
    }

    suspend fun cachedOptions(): SearchOptions? = cached("options", SearchOptions.serializer())

    suspend fun lastRequest(): SearchRequest? = cached("request", SearchRequest.serializer())

    suspend fun rememberRequest(request: SearchRequest) {
        store.put(
            serverId, CACHE, "request",
            SearchCacheEntry(ArrJson.encodeToString(SearchRequest.serializer(), request), now()),
            SearchCacheEntry.serializer(),
        )
    }

    suspend fun search(request: SearchRequest, force: Boolean = false) = load(
        "results:${request.key()}", ListSerializer(ReleaseResource.serializer()), force,
    ) {
        searchApi.getSearch(
            request.query.trim().ifBlank { null }, "search",
            request.indexerIds.sorted().ifEmpty { null },
            request.categories.sorted().ifEmpty { null }, null, null,
        )
    }

    suspend fun cachedSearch(request: SearchRequest): List<ReleaseResource>? = cached(
        "results:${request.key()}", ListSerializer(ReleaseResource.serializer()),
    )

    suspend fun history(page: Int, indexers: Set<Int>, force: Boolean = false) = load(
        historyKey(page, indexers), HistoryResourcePagingResource.serializer(), force,
    ) {
        historyApi.getHistory(
            page, 50, "date", SortDirection.DESCENDING, listOf(2), null, null,
            indexers.sorted().ifEmpty { null },
        )
    }

    suspend fun cachedHistory(page: Int, indexers: Set<Int>) = cached(
        historyKey(page, indexers), HistoryResourcePagingResource.serializer(),
    )

    suspend fun stats(days: Int, force: Boolean = false) = load(
        "stats:$days", IndexerStatsResource.serializer(), force,
    ) {
        val end = Instant.ofEpochMilli(now())
        statsApi.getIndexerStats(
            end.minus(days.toLong(), ChronoUnit.DAYS).toString(), end.toString(), null, null, null,
        )
    }

    suspend fun cachedStats(days: Int) = cached("stats:$days", IndexerStatsResource.serializer())

    /** True means queued, false means the server accepted the grab. */
    suspend fun grab(
        release: ReleaseResource,
        clientId: Int,
    ): ArrResult<Boolean> = withContext(io) {
        val body = release.copy(downloadClientId = clientId)
        try {
            searchApi.createSearch(body)
            ArrResult.ok(false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.searchError()
            if (error !is ArrError.Network && error !is ArrError.Offline) {
                return@withContext ArrResult.err(error)
            }
            // Persist before reporting success so process death cannot lose an offline grab.
            try {
                val encoded = ArrJson.encodeToString(ReleaseResource.serializer(), body)
                val duplicate = queue.pending().any {
                    it.serverId == serverId && it.path == GRAB_PATH && it.bodyJson == encoded
                }
                if (!duplicate) queue.enqueue(
                    QueuedAction(
                        ActionId(UUID.randomUUID().toString()), serverId, AppType.PROWLARR,
                        "POST", GRAB_PATH, encoded, body.title.orEmpty(), now(),
                    ),
                )
                ArrResult.ok(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ArrResult.err(e.searchError())
            }
        }
    }

    fun queuedReleaseKeys(): Flow<Set<String>> = queue.observe().map { actions ->
        actions.filter { it.serverId == serverId && it.path == GRAB_PATH }.mapNotNull {
            runCatching {
                ArrJson.decodeFromString(
                    ReleaseResource.serializer(), it.bodyJson.orEmpty(),
                ).rowKey()
            }.getOrNull()
        }.toSet()
    }

    private fun historyKey(page: Int, indexers: Set<Int>) =
        "history:$page:${indexers.sorted().joinToString(",")}"

    private suspend fun <T> cached(key: String, serializer: KSerializer<T>): T? =
        store.get(serverId, CACHE, key, SearchCacheEntry.serializer())?.let {
            ArrJson.decodeFromString(serializer, it.payload)
        }

    private suspend fun <T> load(
        key: String,
        serializer: KSerializer<T>,
        force: Boolean,
        fetch: suspend () -> T,
    ): ArrResult<T> = withContext(io) {
        try {
            val entry = store.get(serverId, CACHE, key, SearchCacheEntry.serializer())
            if (!force && entry != null && now() - entry.fetchedAt < TTL_MILLIS) {
                return@withContext ArrResult.ok(ArrJson.decodeFromString(serializer, entry.payload))
            }
            val value = fetch()
            store.put(
                serverId, CACHE, key,
                SearchCacheEntry(ArrJson.encodeToString(serializer, value), now()),
                SearchCacheEntry.serializer(),
            )
            ArrResult.ok(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ArrResult.err(e.searchError())
        }
    }

    companion object {
        const val TTL_MILLIS = 300_000L
        internal const val GRAB_PATH = "api/v1/search"
        private val CACHE = EntityType("prowlarr.search.cache")
    }
}

private fun Exception.searchError(): ArrError = when (this) {
    is HttpException -> when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }
    else -> arrError()
}
