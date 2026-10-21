package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.LogResourcePagingResource
import dev.appolon.homelab.arr.api.prowlarr.model.SortDirection
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Log tag: paged server log. `level` has no enum in this spec version, so it
 * stays a plain string the caller passes through.
 */
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
