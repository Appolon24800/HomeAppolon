package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
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

/**
 * Indexer tag: CRUD on configured indexers, the definition schemas, and the
 * test or named-action escapes. PUT path ids are strings because the spec
 * declares that type on update routes. Body-bearing calls return the decoded
 * resource directly; body-less calls return [Response] because the status code
 * is the whole result. Bulk delete uses [HTTP] because Retrofit otherwise
 * rejects a body on DELETE even though the spec documents one.
 */
@JvmSuppressWildcards
interface IndexerApi {
    @GET("api/v1/indexer")
    suspend fun getIndexers(): List<IndexerResource>

    @GET("api/v1/indexer/{id}")
    suspend fun getIndexer(@Path("id") id: Int): IndexerResource

    @GET("api/v1/indexer/schema")
    suspend fun getIndexerSchemas(): List<IndexerResource>

    @POST("api/v1/indexer")
    suspend fun createIndexer(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: IndexerResource,
    ): IndexerResource

    @PUT("api/v1/indexer/{id}")
    suspend fun updateIndexer(
        @Path("id") id: String,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: IndexerResource,
    ): IndexerResource

    @PUT("api/v1/indexer/bulk")
    suspend fun updateIndexers(@Body resource: IndexerBulkResource): IndexerResource

    @DELETE("api/v1/indexer/{id}")
    suspend fun deleteIndexer(@Path("id") id: Int): Response<Unit>

    @HTTP(method = "DELETE", path = "api/v1/indexer/bulk", hasBody = true)
    suspend fun deleteIndexers(@Body resource: IndexerBulkResource): Response<Unit>

    @POST("api/v1/indexer/test")
    suspend fun testIndexer(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body resource: IndexerResource,
    ): Response<Unit>

    @POST("api/v1/indexer/testall")
    suspend fun testAllIndexers(): Response<Unit>

    @POST("api/v1/indexer/action/{name}")
    suspend fun indexerAction(
        @Path("name") name: String,
        @Body resource: IndexerResource,
    ): Response<Unit>
}
