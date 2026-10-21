package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.sonarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueStatus
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Path
import retrofit2.http.Query

/** Operations in the Sonarr Queue OpenAPI tag. */
@JvmSuppressWildcards
interface QueueApi {
    @DELETE("api/v3/queue/{id}")
    suspend fun delete(
        @Path("id") id: Int,
        @Query("removeFromClient") removeFromClient: Boolean? = null,
        @Query("blocklist") blocklist: Boolean? = null,
        @Query("skipRedownload") skipRedownload: Boolean? = null,
        @Query("changeCategory") changeCategory: Boolean? = null
    ): Unit

    @HTTP(method = "DELETE", path = "api/v3/queue/bulk", hasBody = true)
    suspend fun deleteBulk(
        @Query("removeFromClient") removeFromClient: Boolean? = null,
        @Query("blocklist") blocklist: Boolean? = null,
        @Query("skipRedownload") skipRedownload: Boolean? = null,
        @Query("changeCategory") changeCategory: Boolean? = null,
        @Body body: QueueBulkResource
    ): Unit

    @GET("api/v3/queue")
    suspend fun list(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("includeUnknownSeriesItems") includeUnknownSeriesItems: Boolean? = null,
        @Query("includeSeries") includeSeries: Boolean? = null,
        @Query("includeEpisode") includeEpisode: Boolean? = null,
        @Query("seriesIds") seriesIds: List<Int>? = null,
        @Query("protocol") protocol: DownloadProtocol? = null,
        @Query("languages") languages: List<Int>? = null,
        @Query("quality") quality: List<Int>? = null,
        @Query("status") status: List<QueueStatus>? = null
    ): QueueResourcePagingResource
}
