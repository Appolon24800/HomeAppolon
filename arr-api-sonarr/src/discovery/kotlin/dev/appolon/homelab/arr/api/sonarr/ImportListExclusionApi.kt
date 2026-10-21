package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@JvmSuppressWildcards
interface ImportListExclusionApi {
    @GET("api/v3/importlistexclusion")
    suspend fun list(): List<ImportListExclusionResource>

    @POST("api/v3/importlistexclusion")
    suspend fun create(
        @Body body: ImportListExclusionResource
    ): ImportListExclusionResource

    @GET("api/v3/importlistexclusion/paged")
    suspend fun paged(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null
    ): ImportListExclusionResourcePagingResource

    @PUT("api/v3/importlistexclusion/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: ImportListExclusionResource
    ): ImportListExclusionResource

    @DELETE("api/v3/importlistexclusion/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @GET("api/v3/importlistexclusion/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<ImportListExclusionResource>

    @HTTP(method = "DELETE", path = "api/v3/importlistexclusion/bulk", hasBody = true)
    suspend fun deleteBulk(
        @Body body: ImportListExclusionBulkResource
    ): Unit
}
