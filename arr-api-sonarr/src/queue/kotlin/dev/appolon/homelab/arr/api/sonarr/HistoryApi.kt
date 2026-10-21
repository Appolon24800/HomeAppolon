package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeHistoryEventType
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Operations in the Sonarr History OpenAPI tag. */
@JvmSuppressWildcards
interface HistoryApi {
    @GET("api/v3/history")
    suspend fun list(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("includeSeries") includeSeries: Boolean? = null,
        @Query("includeEpisode") includeEpisode: Boolean? = null,
        @Query("eventType") eventType: List<Int>? = null,
        @Query("episodeId") episodeId: Int? = null,
        @Query("downloadId") downloadId: String? = null,
        @Query("seriesIds") seriesIds: List<Int>? = null,
        @Query("languages") languages: List<Int>? = null,
        @Query("quality") quality: List<Int>? = null
    ): HistoryResourcePagingResource

    @GET("api/v3/history/since")
    suspend fun since(
        @Query("date") date: String? = null,
        @Query("eventType") eventType: EpisodeHistoryEventType? = null,
        @Query("includeSeries") includeSeries: Boolean? = null,
        @Query("includeEpisode") includeEpisode: Boolean? = null
    ): List<HistoryResource>

    @GET("api/v3/history/series")
    suspend fun series(
        @Query("seriesId") seriesId: Int? = null,
        @Query("seasonNumber") seasonNumber: Int? = null,
        @Query("eventType") eventType: EpisodeHistoryEventType? = null,
        @Query("includeSeries") includeSeries: Boolean? = null,
        @Query("includeEpisode") includeEpisode: Boolean? = null
    ): List<HistoryResource>

    @POST("api/v3/history/failed/{id}")
    suspend fun markFailed(
        @Path("id") id: Int
    ): Unit
}
