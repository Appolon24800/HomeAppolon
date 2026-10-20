package dev.appolon.homelab.arr.feature.radarr.wanted.data

import dev.appolon.homelab.arr.api.radarr.CutoffApi
import dev.appolon.homelab.arr.api.radarr.MissingApi
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
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
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieCommandRunner
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import retrofit2.HttpException

/** Server page size for the wanted endpoints; one load-more fetches one page. */
const val WANTED_PAGE_SIZE = 50

/**
 * Paging cursor per tab: the server total (drives end-of-list) plus how many
 * pages the cache already holds.
 */
@Serializable
data class WantedTabMeta(
    val totalRecords: Int = 0,
    val pagesLoaded: Int = 0,
) {
    /** True when every server page is already in the cache. */
    fun endReached(cachedCount: Int): Boolean = cachedCount >= totalRecords
}

/** Result of appending one server page to the cache. */
data class WantedPageLoad(
    val appended: Int,
    val meta: WantedTabMeta,
    val endReached: Boolean,
)

/**
 * Wanted repository (missing and cutoff unmet). Reads are cache-first
 * through the [EntityStore] with a per-tab fetchedAt TTL; refresh re-fetches
 * the first [REFRESH_PAGES] server pages and re-centers the cache on them,
 * load-more appends the next server page. Searches dispatch the
 * `MoviesSearch` command and, when the network fails retryably, enqueue for
 * replay through the [ActionQueue]. Nothing here throws; everything runs on
 * [io]. Tab refreshes must run sequentially when both tabs write the shared
 * counts entity; the ViewModel owns that ordering.
 */
class RadarrWantedRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val missingApi: MissingApi,
    private val cutoffApi: CutoffApi,
    private val qualityProfileApi: suspend () -> List<QualityProfileResource>,
    private val commandRunner: MovieCommandRunner,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun observe(tab: WantedTab): Flow<List<MovieResource>> =
        store.observe(serverId, tab.entityType, MovieResource.serializer())

    suspend fun cached(tab: WantedTab): List<MovieResource> =
        store.getAll(serverId, tab.entityType, MovieResource.serializer())

    /** True when the tab's cache is absent or older than [ttlMillis]. */
    suspend fun isStale(tab: WantedTab, ttlMillis: Long = WANTED_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, tab.entityType) ?: return true
        return now() - fetched > ttlMillis
    }

    /**
     * Re-fetches the first [REFRESH_PAGES] server pages and replaces the
     * tab's cache with them, mirroring the web UI recentering on page one.
     * Also refreshes the shared totals entity the shell badge reads.
     */
    suspend fun refresh(tab: WantedTab): ArrResult<WantedTabMeta> = withContext(io) {
        val first = when (val page = fetch(tab, PAGE_ONE)) {
            is ArrResult.Err -> return@withContext page
            is ArrResult.Ok -> page.value
        }
        var combined = first
        var page = PAGE_ONE
        while (page < REFRESH_PAGES &&
            combined.records.orEmpty().size >= page * WANTED_PAGE_SIZE
        ) {
            // The counter only advances on success so a failed follow-up page
            // cannot leave pagesLoaded ahead of what the cache holds.
            when (val next = fetch(tab, page + 1)) {
                is ArrResult.Err -> break
                is ArrResult.Ok -> {
                    page += 1
                    combined = mergePages(combined, next.value)
                }
            }
        }
        val records = combined.records.orEmpty()
        store.deleteAll(serverId, tab.entityType)
        store.putAll(
            serverId,
            tab.entityType,
            records.map { it.id.toString() to it },
            MovieResource.serializer(),
            fetchedAtMillis = now(),
        )
        val meta = WantedTabMeta(totalRecords = combined.totalRecords, pagesLoaded = page)
        putMeta(tab, meta)
        updateCounts(tab, combined.totalRecords)
        ArrResult.ok(meta)
    }

    /** Refresh only when the TTL expired. */
    suspend fun refreshIfStale(tab: WantedTab): ArrResult<WantedTabMeta> =
        if (isStale(tab)) refresh(tab) else ArrResult.ok(metaOf(tab))

    /**
     * Appends the next unfetched server page to the cache. The fetchedAt
     * stamp is carried over so a deep scroll does not extend the refresh TTL.
     */
    suspend fun loadNextPage(tab: WantedTab): ArrResult<WantedPageLoad> = withContext(io) {
        val current = cached(tab)
        val meta = metaOf(tab)
        if (meta.endReached(current.size)) {
            return@withContext ArrResult.ok(WantedPageLoad(0, meta, endReached = true))
        }
        when (val page = fetch(tab, meta.pagesLoaded + 1)) {
            is ArrResult.Err -> return@withContext page
            is ArrResult.Ok -> {
                val records = page.value.records.orEmpty()
                val stamp = store.lastFetchedAt(serverId, tab.entityType) ?: now()
                store.putAll(
                    serverId,
                    tab.entityType,
                    records.map { it.id.toString() to it },
                    MovieResource.serializer(),
                    fetchedAtMillis = stamp,
                )
                val nextMeta = WantedTabMeta(
                    totalRecords = page.value.totalRecords,
                    pagesLoaded = meta.pagesLoaded + 1,
                )
                putMeta(tab, nextMeta)
                updateCounts(tab, page.value.totalRecords)
                ArrResult.ok(
                    WantedPageLoad(
                        appended = records.size,
                        meta = nextMeta,
                        endReached = nextMeta.endReached(cached(tab).size),
                    ),
                )
            }
        }
    }

    /** Quality profiles for filter labels, cached with its own TTL. */
    suspend fun qualityProfiles(
        ttlMillis: Long = PROFILE_TTL_MILLIS,
    ): ArrResult<List<QualityProfileResource>> =
        withContext(io) {
            val cachedProfiles =
                store.getAll(serverId, PROFILE_TYPE, QualityProfileResource.serializer())
            val fetched = store.lastFetchedAt(serverId, PROFILE_TYPE)
            if (fetched != null && now() - fetched <= ttlMillis && cachedProfiles.isNotEmpty()) {
                return@withContext ArrResult.ok(cachedProfiles)
            }
            val profiles = try {
                qualityProfileApi()
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            if (profiles.isNotEmpty()) {
                store.putAll(
                    serverId,
                    PROFILE_TYPE,
                    profiles.map { it.id.toString() to it },
                    QualityProfileResource.serializer(),
                    fetchedAtMillis = now(),
                )
                ArrResult.ok(profiles)
            } else {
                ArrResult.ok(cachedProfiles)
            }
        }

    /**
     * Dispatches `MoviesSearch` for [movieIds]. On offline or network failure
     * the command queues for replay; the caller learns which via
     * [WriteOutcome] and can badge the affected rows.
     */
    suspend fun searchMovies(movieIds: List<Int>): ArrResult<WriteOutcome> =
        withContext(io) {
            if (movieIds.isEmpty()) return@withContext ArrResult.ok(WriteOutcome.Synced)
            val error = try {
                val result = commandRunner.run(SEARCH_COMMAND, movieIds)
                if (result is ArrResult.Ok) {
                    return@withContext ArrResult.ok(WriteOutcome.Synced)
                }
                (result as ArrResult.Err).error
            } catch (e: Throwable) {
                e.arrError()
            }
            if (!error.isRetryable()) {
                return@withContext ArrResult.err(error)
            }
            val body = buildJsonObject {
                put("name", SEARCH_COMMAND)
                put("movieIds", JsonArray(movieIds.map { JsonPrimitive(it) }))
            }
            val action = QueuedAction(
                actionId = ActionId(UUID.randomUUID().toString()),
                serverId = serverId,
                appType = AppType.RADARR,
                method = "POST",
                path = COMMAND_PATH,
                bodyJson = body.toString(),
                description = "Command $SEARCH_COMMAND",
                enqueuedAtMillis = now(),
            )
            actionQueue.enqueue(action)
            ArrResult.ok(WriteOutcome.Queued(action.actionId))
        }

    /** Movie ids with a queued search, for per-row queued badges. */
    fun queuedSearchMovieIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.path == COMMAND_PATH }
            .flatMap { action ->
                runCatching {
                    ArrJson.parseToJsonElement(action.bodyJson.orEmpty()).jsonObject
                }.getOrNull()
                    ?.get("movieIds")
                    ?.jsonArray
                    ?.mapNotNull { runCatching { it.jsonPrimitive.int }.getOrNull() }
                    .orEmpty()
            }
            .toSet()
    }

    /** Count of queued actions for this server, for the toolbar indicator. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    /** Live server totals for both tabs, for the shell badge callback. */
    fun observeCounts(): Flow<WantedCounts> =
        store.observe(serverId, COUNTS_TYPE, WantedCounts.serializer())
            .map { rows -> rows.firstOrNull() ?: WantedCounts() }

    /** Snapshot of the tab meta, defaults when never refreshed. */
    suspend fun metaOf(tab: WantedTab): WantedTabMeta =
        store.get(serverId, META_TYPE, tab.wireName, WantedTabMeta.serializer())
            ?: WantedTabMeta()

    private suspend fun putMeta(tab: WantedTab, meta: WantedTabMeta) {
        store.put(serverId, META_TYPE, tab.wireName, meta, WantedTabMeta.serializer())
    }

    /** Read-modify-write of the one-row totals entity both tabs share. */
    private suspend fun updateCounts(tab: WantedTab, totalRecords: Int) {
        val current = store.get(serverId, COUNTS_TYPE, COUNTS_ID, WantedCounts.serializer())
            ?: WantedCounts()
        val updated = when (tab) {
            WantedTab.MISSING -> current.copy(missing = totalRecords)
            WantedTab.CUTOFF -> current.copy(cutoff = totalRecords)
        }
        store.put(serverId, COUNTS_TYPE, COUNTS_ID, updated, WantedCounts.serializer())
    }

    private suspend fun fetch(
        tab: WantedTab,
        page: Int,
    ): ArrResult<MovieResourcePagingResource> {
        // Stable sort across pages keeps append order coherent with page one.
        val sortKey = "sortTitle"
        return try {
            val result = when (tab) {
                WantedTab.MISSING -> missingApi.missing(
                    page = page,
                    pageSize = WANTED_PAGE_SIZE,
                    sortKey = sortKey,
                    sortDirection = SortDirection.ASCENDING,
                    monitored = null,
                )

                WantedTab.CUTOFF -> cutoffApi.cutoff(
                    page = page,
                    pageSize = WANTED_PAGE_SIZE,
                    sortKey = sortKey,
                    sortDirection = SortDirection.ASCENDING,
                    monitored = null,
                )
            }
            ArrResult.ok(result)
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    private fun mergePages(
        base: MovieResourcePagingResource,
        next: MovieResourcePagingResource,
    ): MovieResourcePagingResource = base.copy(
        records = (base.records.orEmpty() + next.records.orEmpty()).distinctBy { it.id },
        totalRecords = next.totalRecords,
    )

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        /** Five minutes; wanted lists shrink as downloads land. */
        const val WANTED_TTL_MILLIS: Long = 5 * 60 * 1000

        /** One hour; profiles change rarely. */
        const val PROFILE_TTL_MILLIS: Long = 60 * 60 * 1000

        /** Pull-to-refresh recenters the cache on the first pages. */
        const val REFRESH_PAGES = 2

        const val SEARCH_COMMAND = "MoviesSearch"
        const val COMMAND_PATH = "api/v3/command"
        private const val PAGE_ONE = 1
        private const val COUNTS_ID = "counts"

        internal val MISSING_TYPE = EntityType("radarr.wanted.missing")
        internal val CUTOFF_TYPE = EntityType("radarr.wanted.cutoff")
        internal val META_TYPE = EntityType("radarr.wanted.meta")
        internal val COUNTS_TYPE = EntityType("radarr.wanted.counts")
        internal val PROFILE_TYPE = EntityType("radarr.qualityprofile")
    }
}

/** Per-tab entity type: one list per tab, both cached side by side. */
val WantedTab.entityType: EntityType
    get() = when (this) {
        WantedTab.MISSING -> RadarrWantedRepository.MISSING_TYPE
        WantedTab.CUTOFF -> RadarrWantedRepository.CUTOFF_TYPE
    }
