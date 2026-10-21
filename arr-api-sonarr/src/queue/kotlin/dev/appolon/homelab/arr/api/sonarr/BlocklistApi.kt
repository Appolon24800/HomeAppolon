package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.BlocklistResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Path
import retrofit2.http.Query

/** Operations in the Sonarr Blocklist OpenAPI tag. */
@JvmSuppressWildcards
interface BlocklistApi {
    @GET("api/v3/blocklist")
    suspend fun list(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("seriesIds") seriesIds: List<Int>? = null,
        @Query("protocols") protocols: List<DownloadProtocol>? = null
    ): BlocklistResourcePagingResource

    @DELETE("api/v3/blocklist/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @HTTP(method = "DELETE", path = "api/v3/blocklist/bulk", hasBody = true)
    suspend fun deleteBulk(
        @Body body: BlocklistBulkResource
    ): Unit
}
