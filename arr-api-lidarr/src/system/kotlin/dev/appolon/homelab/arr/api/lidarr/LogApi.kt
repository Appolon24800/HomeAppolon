package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.LogResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Query

/** Lidarr endpoints tagged Log for the paged server log table. */
@JvmSuppressWildcards
interface LogApi {
    @GET("api/v1/log")
    suspend fun getLog(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("level") level: String? = null,
    ): LogResourcePagingResource
}
