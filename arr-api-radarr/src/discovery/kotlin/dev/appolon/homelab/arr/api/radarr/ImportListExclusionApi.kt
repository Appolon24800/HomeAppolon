package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionBulkResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr endpoints tagged ImportListExclusion. */
interface ImportListExclusionApi {
    @GET("api/v3/exclusions")
    suspend fun exclusions(): List<ImportListExclusionResource>

    @POST("api/v3/exclusions")
    suspend fun createExclusion(
        @Body body: ImportListExclusionResource,
    ): Response<ImportListExclusionResource>

    @GET("api/v3/exclusions/paged")
    suspend fun exclusionsPaged(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
    ): ImportListExclusionResourcePagingResource

    @PUT("api/v3/exclusions/{id}")
    suspend fun updateExclusion(
        @Path("id") id: String,
        @Body body: ImportListExclusionResource,
    ): Response<ImportListExclusionResource>

    @DELETE("api/v3/exclusions/{id}")
    suspend fun removeExclusion(
        @Path("id") id: Int,
    ): Response<Unit>

    @GET("api/v3/exclusions/{id}")
    suspend fun exclusion(
        @Path("id") id: Int,
    ): Response<ImportListExclusionResource>

    @POST("api/v3/exclusions/bulk")
    suspend fun addExclusionsBulk(
        @Body body: List<ImportListExclusionResource>,
    ): List<ImportListExclusionResource>

    /** The spec leaves the response schema unspecified. */
    @HTTP(method = "DELETE", path = "api/v3/exclusions/bulk", hasBody = true)
    suspend fun removeExclusionsBulk(
        @Body body: ImportListExclusionBulkResource,
    ): JsonObject

}
