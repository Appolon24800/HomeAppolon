package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.HistoryEventType
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResource
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.prowlarr.model.SortDirection
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * History tag: what the server queried, grabbed and authenticated, paged for
 * the history screen or sliced by time window and indexer for detail screens.
 * Date queries stay wire-format strings; the UI formats them with
 * :arr-core ArrTime.
 */
@JvmSuppressWildcards
interface HistoryApi {
    @GET("api/v1/history")
    suspend fun getHistory(
        @Query("page") page: Int?,
        @Query("pageSize") pageSize: Int?,
        @Query("sortKey") sortKey: String?,
        @Query("sortDirection") sortDirection: SortDirection?,
        @Query("eventType") eventType: List<Int>?,
        @Query("successful") successful: Boolean?,
        @Query("downloadId") downloadId: String?,
        @Query("indexerIds") indexerIds: List<Int>?,
    ): HistoryResourcePagingResource

    @GET("api/v1/history/since")
    suspend fun getHistorySince(
        @Query("date") date: String?,
        @Query("eventType") eventType: HistoryEventType?,
    ): List<HistoryResource>

    @GET("api/v1/history/indexer")
    suspend fun getHistoryForIndexer(
        @Query("indexerId") indexerId: Int?,
        @Query("eventType") eventType: HistoryEventType?,
        @Query("limit") limit: Int?,
    ): List<HistoryResource>
}
