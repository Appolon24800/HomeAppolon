package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
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
interface ImportListApi {
    @GET("api/v3/importlist")
    suspend fun list(): List<ImportListResource>

    @POST("api/v3/importlist")
    suspend fun create(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: ImportListResource
    ): ImportListResource

    @PUT("api/v3/importlist/{id}")
    suspend fun update(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: ImportListResource
    ): ImportListResource

    @DELETE("api/v3/importlist/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @GET("api/v3/importlist/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<ImportListResource>

    @PUT("api/v3/importlist/bulk")
    suspend fun updateBulk(
        @Body body: ImportListBulkResource
    ): ImportListResource

    @HTTP(method = "DELETE", path = "api/v3/importlist/bulk", hasBody = true)
    suspend fun deleteBulk(
        @Body body: ImportListBulkResource
    ): Unit

    @GET("api/v3/importlist/schema")
    suspend fun schema(): List<ImportListResource>

    @POST("api/v3/importlist/test")
    suspend fun test(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body body: ImportListResource
    ): Unit

    @POST("api/v3/importlist/testall")
    suspend fun testAll(): Unit

    @POST("api/v3/importlist/action/{name}")
    suspend fun action(
        @Path("name") name: String,
        @Body body: ImportListResource
    ): Unit
}
