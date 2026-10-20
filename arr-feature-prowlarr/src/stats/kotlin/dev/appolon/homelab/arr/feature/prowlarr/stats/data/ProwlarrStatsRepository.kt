package dev.appolon.homelab.arr.feature.prowlarr.stats.data

import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.net.arrError
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Stats repository. One [IndexerStatsResource] per [StatsPeriod] is cached in
 * the entity store with a fetchedAt TTL; reads are cache-first and a failed
 * refresh falls back to the stale window so the screen keeps working offline.
 * The endpoint is read-only, so there is no write path here.
 */
class ProwlarrStatsRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val statsApi: suspend (startDate: String?, endDate: String?) -> IndexerStatsResource,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Emits the cached resource of [period] whenever any stats row changes. */
    fun observeStats(period: StatsPeriod): Flow<IndexerStatsResource?> = observeAll()
        .map { rows -> rows.firstOrNull { it.period == period.wireName }?.resource }

    /** Emits every cached stats window keyed by period wire name. */
    fun observeAll(): Flow<List<CachedStats>> =
        store.observe(serverId, STATS_TYPE, CachedStats.serializer())

    suspend fun cachedStats(period: StatsPeriod): IndexerStatsResource? =
        allCached().firstOrNull { it.period == period.wireName }?.resource

    suspend fun isStale(period: StatsPeriod, ttlMillis: Long = STATS_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, STATS_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /**
     * Cache-first read for [period]. Within the TTL the cached window returns
     * without network; past it, or for [refresh], the endpoint is queried with
     * the period window and the cache is replaced. A fetch failure returns an
     * error without deleting the cached window.
     */
    suspend fun stats(
        period: StatsPeriod,
        ttlMillis: Long = STATS_TTL_MILLIS,
    ): ArrResult<IndexerStatsResource> = withContext(io) {
        val cached = cachedStats(period)
        if (cached != null && !isStale(period, ttlMillis)) {
            return@withContext ArrResult.ok(cached)
        }
        fetch(period)
    }

    /** Force fetch of [period] regardless of the TTL; keeps stale data on error. */
    suspend fun refresh(period: StatsPeriod): ArrResult<IndexerStatsResource> = withContext(io) {
        fetch(period)
    }

    private suspend fun fetch(
        period: StatsPeriod,
    ): ArrResult<IndexerStatsResource> {
        val window = period.window(Instant.ofEpochMilli(now()))
        val fetched = try {
            statsApi(window.first, window.second)
        } catch (e: HttpException) {
            return ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return ArrResult.err(e.arrError())
        }
        store.putAll(
            serverId, STATS_TYPE,
            listOf(period.wireName to CachedStats(period.wireName, fetched)),
            CachedStats.serializer(),
            fetchedAtMillis = now(),
        )
        return ArrResult.ok(fetched)
    }

    private suspend fun allCached(): List<CachedStats> =
        store.getAll(serverId, STATS_TYPE, CachedStats.serializer())

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        internal val STATS_TYPE = EntityType("prowlarr.indexerstats")

        /** Five minutes: counters move constantly, but not second to second. */
        const val STATS_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.LIBRARY
    }
}
