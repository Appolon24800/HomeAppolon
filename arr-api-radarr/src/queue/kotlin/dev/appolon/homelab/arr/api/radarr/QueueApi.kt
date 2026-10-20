package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.radarr.model.QueueResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.QueueStatus
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr endpoints tagged Queue. */
@JvmSuppressWildcards

interface QueueApi {
    @DELETE("api/v3/queue/{id}")
    suspend fun removeQueue(
        @Path("id") id: Int,
        @Query("removeFromClient") removeFromClient: Boolean? = null,
        @Query("blocklist") blocklist: Boolean? = null,
        @Query("skipRedownload") skipRedownload: Boolean? = null,
        @Query("changeCategory") changeCategory: Boolean? = null,
    ): Response<Unit>

    /** The spec leaves the response schema unspecified. */
    @HTTP(method = "DELETE", path = "api/v3/queue/bulk", hasBody = true)
    suspend fun removeQueueBulk(
        @Query("removeFromClient") removeFromClient: Boolean? = null,
        @Query("blocklist") blocklist: Boolean? = null,
        @Query("skipRedownload") skipRedownload: Boolean? = null,
        @Query("changeCategory") changeCategory: Boolean? = null,
        @Body body: QueueBulkResource,
    ): JsonObject

    @GET("api/v3/queue")
    suspend fun queue(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("includeUnknownMovieItems") includeUnknownMovieItems: Boolean? = null,
        @Query("includeMovie") includeMovie: Boolean? = null,
        @Query("movieIds") movieIds: List<Int>? = null,
        @Query("protocol") protocol: DownloadProtocol? = null,
        @Query("languages") languages: List<Int>? = null,
        @Query("quality") quality: List<Int>? = null,
        @Query("status") status: List<QueueStatus>? = null,
    ): QueueResourcePagingResource

}
