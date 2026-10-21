package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerResource
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

/** Radarr endpoints tagged Indexer. */
@JvmSuppressWildcards
interface IndexerApi {
    @GET("api/v3/indexer")
    suspend fun indexers(): List<IndexerResource>

    @POST("api/v3/indexer")
    suspend fun createIndexer(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: IndexerResource,
    ): Response<IndexerResource>

    @PUT("api/v3/indexer/{id}")
    suspend fun updateIndexer(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: IndexerResource,
    ): Response<IndexerResource>

    /** The spec leaves the response schema unspecified. */
    @DELETE("api/v3/indexer/{id}")
    suspend fun removeIndexer(
        @Path("id") id: Int,
    ): JsonObject

    @GET("api/v3/indexer/{id}")
    suspend fun indexer(
        @Path("id") id: Int,
    ): Response<IndexerResource>

    @PUT("api/v3/indexer/bulk")
    suspend fun updateIndexerBulk(
        @Body body: IndexerBulkResource,
    ): Response<List<IndexerResource>>

    /** The spec leaves the response schema unspecified. */
    @HTTP(method = "DELETE", path = "api/v3/indexer/bulk", hasBody = true)
    suspend fun removeIndexerBulk(
        @Body body: IndexerBulkResource,
    ): JsonObject

    @GET("api/v3/indexer/schema")
    suspend fun indexerSchema(): List<IndexerResource>

    @POST("api/v3/indexer/test")
    suspend fun testIndexer(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body body: IndexerResource,
    ): Response<Unit>

    @POST("api/v3/indexer/testall")
    suspend fun testAllIndexers(): Response<List<ProviderTestAllResult>>

    /** The spec documents only a 400 response, so success carries no body. */
    @POST("api/v3/indexer/action/{name}")
    suspend fun indexerAction(
        @Path("name") name: String,
        @Body body: IndexerResource,
    ): Response<Unit>

}
