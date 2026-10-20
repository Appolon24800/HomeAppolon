package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * IndexerStats tag: query, grab and failure counters rolled up per indexer,
 * user agent and host for the stats screen. The window and grouping filters
 * are server-side strings (`indexers`, `protocols`, `tags`) exactly as the
 * spec declares them.
 */
@JvmSuppressWildcards
interface IndexerStatsApi {
    @GET("api/v1/indexerstats")
    suspend fun getIndexerStats(
        @Query("startDate") startDate: String?,
        @Query("endDate") endDate: String?,
        @Query("indexers") indexers: String?,
        @Query("protocols") protocols: String?,
        @Query("tags") tags: String?,
    ): IndexerStatsResource
}
