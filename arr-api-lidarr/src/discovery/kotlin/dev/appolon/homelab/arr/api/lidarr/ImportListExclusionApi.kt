package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ImportListExclusionResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged ImportListExclusion. The update path id is a string in
 * the spec, unlike the integer ids on get and delete.
 */
@JvmSuppressWildcards
interface ImportListExclusionApi {
    @GET("api/v1/importlistexclusion")
    suspend fun getImportListExclusions(): List<ImportListExclusionResource>

    @GET("api/v1/importlistexclusion/{id}")
    suspend fun getImportListExclusion(@Path("id") id: Int): ImportListExclusionResource

    @POST("api/v1/importlistexclusion")
    suspend fun addImportListExclusion(
        @Body resource: ImportListExclusionResource,
    ): ImportListExclusionResource

    @PUT("api/v1/importlistexclusion/{id}")
    suspend fun updateImportListExclusion(
        @Path("id") id: String,
        @Body resource: ImportListExclusionResource,
    ): ImportListExclusionResource

    @DELETE("api/v1/importlistexclusion/{id}")
    suspend fun deleteImportListExclusion(@Path("id") id: Int)
}
