package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerResource
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
 * Lidarr endpoints tagged Indexer. The bulk PUT response is typed as a single
 * resource in the spec even though it edits many.
 */
@JvmSuppressWildcards
interface IndexerApi {
    @GET("api/v1/indexer")
    suspend fun getIndexers(): List<IndexerResource>

    @GET("api/v1/indexer/{id}")
    suspend fun getIndexer(@Path("id") id: Int): IndexerResource

    @POST("api/v1/indexer")
    suspend fun addIndexer(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: IndexerResource,
    ): IndexerResource

    @PUT("api/v1/indexer/{id}")
    suspend fun updateIndexer(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: IndexerResource,
    ): IndexerResource

    @DELETE("api/v1/indexer/{id}")
    suspend fun deleteIndexer(@Path("id") id: Int)

    @PUT("api/v1/indexer/bulk")
    suspend fun updateIndexers(
        @Body resource: IndexerBulkResource,
    ): IndexerResource

    @HTTP(method = "DELETE", path = "api/v1/indexer/bulk", hasBody = true)
    suspend fun deleteIndexers(@Body resource: IndexerBulkResource)

    @GET("api/v1/indexer/schema")
    suspend fun getIndexerSchema(): List<IndexerResource>

    @POST("api/v1/indexer/test")
    suspend fun testIndexer(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body resource: IndexerResource,
    )

    @POST("api/v1/indexer/testall")
    suspend fun testAllIndexers()

    @POST("api/v1/indexer/action/{name}")
    suspend fun indexerAction(
        @Path("name") name: String,
        @Body resource: IndexerResource,
    )
}
