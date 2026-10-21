package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.IndexerConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged IndexerConfig. The update path id is a string in the
 * spec, unlike the integer id on the single get.
 */
@JvmSuppressWildcards
interface IndexerConfigApi {
    @GET("api/v1/config/indexer")
    suspend fun getIndexerConfig(): IndexerConfigResource

    @GET("api/v1/config/indexer/{id}")
    suspend fun getIndexerConfigById(@Path("id") id: Int): IndexerConfigResource

    @PUT("api/v1/config/indexer/{id}")
    suspend fun updateIndexerConfig(
        @Path("id") id: String,
        @Body resource: IndexerConfigResource,
    ): IndexerConfigResource
}
