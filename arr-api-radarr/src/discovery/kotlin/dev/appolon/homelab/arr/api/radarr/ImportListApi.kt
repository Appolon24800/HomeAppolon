package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListResource
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
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

/** Radarr endpoints tagged ImportList. */
interface ImportListApi {
    @GET("api/v3/importlist")
    suspend fun importLists(): List<ImportListResource>

    @POST("api/v3/importlist")
    suspend fun createImportList(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: ImportListResource,
    ): Response<ImportListResource>

    @PUT("api/v3/importlist/{id}")
    suspend fun updateImportList(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: ImportListResource,
    ): Response<ImportListResource>

    /** The spec leaves the response schema unspecified. */
    @DELETE("api/v3/importlist/{id}")
    suspend fun removeImportList(
        @Path("id") id: Int,
    ): JsonObject

    @GET("api/v3/importlist/{id}")
    suspend fun importList(
        @Path("id") id: Int,
    ): Response<ImportListResource>

    @PUT("api/v3/importlist/bulk")
    suspend fun updateImportListBulk(
        @Body body: ImportListBulkResource,
    ): Response<List<ImportListResource>>

    /** The spec leaves the response schema unspecified. */
    @HTTP(method = "DELETE", path = "api/v3/importlist/bulk", hasBody = true)
    suspend fun removeImportListBulk(
        @Body body: ImportListBulkResource,
    ): JsonObject

    @GET("api/v3/importlist/schema")
    suspend fun importListSchema(): List<ImportListResource>

    @POST("api/v3/importlist/test")
    suspend fun testImportList(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body body: ImportListResource,
    ): Response<Unit>

    @POST("api/v3/importlist/testall")
    suspend fun testAllImportLists(): Response<List<ProviderTestAllResult>>

    /** The spec documents only a 400 response, so success carries no body. */
    @POST("api/v3/importlist/action/{name}")
    suspend fun importListAction(
        @Path("name") name: String,
        @Body body: ImportListResource,
    ): Response<Unit>

}
