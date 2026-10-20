package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerResource
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
interface IndexerApi {
    @GET("api/v3/indexer")
    suspend fun list(): List<IndexerResource>

    @POST("api/v3/indexer")
    suspend fun create(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: IndexerResource
    ): IndexerResource

    @PUT("api/v3/indexer/{id}")
    suspend fun update(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: IndexerResource
    ): IndexerResource

    @DELETE("api/v3/indexer/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @GET("api/v3/indexer/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<IndexerResource>

    @PUT("api/v3/indexer/bulk")
    suspend fun updateBulk(
        @Body body: IndexerBulkResource
    ): IndexerResource

    @HTTP(method = "DELETE", path = "api/v3/indexer/bulk", hasBody = true)
    suspend fun deleteBulk(
        @Body body: IndexerBulkResource
    ): Unit

    @GET("api/v3/indexer/schema")
    suspend fun schema(): List<IndexerResource>

    @POST("api/v3/indexer/test")
    suspend fun test(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body body: IndexerResource
    ): Unit

    @POST("api/v3/indexer/testall")
    suspend fun testAll(): Unit

    @POST("api/v3/indexer/action/{name}")
    suspend fun action(
        @Path("name") name: String,
        @Body body: IndexerResource
    ): Unit
}
