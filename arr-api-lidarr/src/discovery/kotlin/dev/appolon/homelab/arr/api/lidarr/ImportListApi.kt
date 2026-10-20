package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Lidarr endpoints tagged ImportList. The bulk PUT response is typed as a single
 * resource because the spec declares a single ImportListResource schema.
 */
@JvmSuppressWildcards
interface ImportListApi {
    @GET("api/v1/importlist")
    suspend fun getImportLists(): List<ImportListResource>

    @GET("api/v1/importlist/{id}")
    suspend fun getImportList(@Path("id") id: Int): ImportListResource

    @GET("api/v1/importlist/schema")
    suspend fun getImportListSchema(): List<ImportListResource>

    @POST("api/v1/importlist")
    suspend fun addImportList(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: ImportListResource,
    ): ImportListResource

    @PUT("api/v1/importlist/{id}")
    suspend fun updateImportList(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: ImportListResource,
    ): ImportListResource

    @PUT("api/v1/importlist/bulk")
    suspend fun updateImportLists(
        @Body resource: ImportListBulkResource,
    ): ImportListResource

    @HTTP(method = "DELETE", path = "api/v1/importlist/bulk", hasBody = true)
    suspend fun deleteImportLists(@Body resource: ImportListBulkResource)

    @DELETE("api/v1/importlist/{id}")
    suspend fun deleteImportList(@Path("id") id: Int)

    @POST("api/v1/importlist/test")
    suspend fun testImportList(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body resource: ImportListResource,
    )

    @POST("api/v1/importlist/testall")
    suspend fun testAllImportLists()

    @POST("api/v1/importlist/action/{name}")
    suspend fun importListAction(
        @Path("name") name: String,
        @Body resource: ImportListResource,
    )
}
