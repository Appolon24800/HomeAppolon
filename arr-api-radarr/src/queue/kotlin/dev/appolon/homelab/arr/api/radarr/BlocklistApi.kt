package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.radarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.radarr.model.BlocklistResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr endpoints tagged Blocklist. */
@JvmSuppressWildcards

interface BlocklistApi {
    @GET("api/v3/blocklist")
    suspend fun blocklist(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("movieIds") movieIds: List<Int>? = null,
        @Query("protocols") protocols: List<DownloadProtocol>? = null,
    ): BlocklistResourcePagingResource

    @GET("api/v3/blocklist/movie")
    suspend fun movieBlocklist(
        @Query("movieId") movieId: Int? = null,
    ): List<BlocklistResource>

    @DELETE("api/v3/blocklist/{id}")
    suspend fun removeBlocklist(
        @Path("id") id: Int,
    ): Response<Unit>

    /** The spec leaves the response schema unspecified. */
    @HTTP(method = "DELETE", path = "api/v3/blocklist/bulk", hasBody = true)
    suspend fun removeBlocklistBulk(
        @Body body: BlocklistBulkResource,
    ): JsonObject

}
