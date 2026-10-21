package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.HistoryResource
import dev.appolon.homelab.arr.api.radarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.MovieHistoryEventType
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr endpoints tagged History. */
@JvmSuppressWildcards

interface HistoryApi {
    @GET("api/v3/history")
    suspend fun history(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("includeMovie") includeMovie: Boolean? = null,
        @Query("eventType") eventType: List<Int>? = null,
        @Query("downloadId") downloadId: String? = null,
        @Query("movieIds") movieIds: List<Int>? = null,
        @Query("languages") languages: List<Int>? = null,
        @Query("quality") quality: List<Int>? = null,
    ): HistoryResourcePagingResource

    @GET("api/v3/history/since")
    suspend fun historySince(
        @Query("date") date: String? = null,
        @Query("eventType") eventType: MovieHistoryEventType? = null,
        @Query("includeMovie") includeMovie: Boolean? = null,
    ): List<HistoryResource>

    @GET("api/v3/history/movie")
    suspend fun movieHistory(
        @Query("movieId") movieId: Int? = null,
        @Query("eventType") eventType: MovieHistoryEventType? = null,
        @Query("includeMovie") includeMovie: Boolean? = null,
    ): List<HistoryResource>

    /** The spec leaves the response schema unspecified. */
    @POST("api/v3/history/failed/{id}")
    suspend fun markHistoryFailed(
        @Path("id") id: Int,
    ): JsonObject

}
