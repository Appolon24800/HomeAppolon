package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.LogResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import retrofit2.http.GET
import retrofit2.http.Query

/** Radarr endpoints tagged Log. */
@JvmSuppressWildcards
interface LogApi {
    @GET("api/v3/log")
    suspend fun log(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("level") level: String? = null,
    ): LogResourcePagingResource
}
