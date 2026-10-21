package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.LogResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Query

/** Operations in the Sonarr Log OpenAPI tag. */
@JvmSuppressWildcards
interface LogApi {
    @GET("api/v3/log")
    suspend fun list(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("level") level: String? = null
    ): LogResourcePagingResource
}
