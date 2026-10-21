package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.BlocklistResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Path
import retrofit2.http.Query

interface BlocklistApi {
    @GET("api/v1/blocklist")
    suspend fun getBlocklist(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
    ): BlocklistResourcePagingResource

    @DELETE("api/v1/blocklist/{id}")
    suspend fun deleteBlocklist(@Path("id") id: Int)

    @HTTP(method = "DELETE", path = "api/v1/blocklist/bulk", hasBody = true)
    suspend fun deleteBlocklistBulk(@Body body: BlocklistBulkResource)
}
